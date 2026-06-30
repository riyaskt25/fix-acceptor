package com.demo.fix.acceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import quickfix.UnsupportedMessageType;
import quickfix.field.ClOrdID;
import quickfix.field.HandlInst;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;
import quickfix.fix44.NewOrderSingle;

@Component
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuickFixAcceptorService extends MessageCracker implements ApplicationRunner, DisposableBean, Application {

	private static final Logger log = LoggerFactory.getLogger(QuickFixAcceptorService.class);

	private final FixAcceptorProperties properties;
	private final ScheduledExecutorService scheduler;
	private final Map<SessionID, OrderFlow> orderFlows = new ConcurrentHashMap<>();

	private volatile SocketAcceptor acceptor;

	public QuickFixAcceptorService(FixAcceptorProperties properties) {
		this.properties = properties;
		ThreadFactory threadFactory = runnable -> {
			Thread thread = new Thread(runnable, "fix-order-scheduler");
			thread.setDaemon(false);
			return thread;
		};
		this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		start();
	}

	private synchronized void start() throws Exception {
		if (acceptor != null) {
			return;
		}

		if (properties.getSessions().isEmpty()) {
			throw new IllegalStateException("At least one FIX acceptor session must be configured");
		}

		Path baseDirectory = Path.of("fix-runtime");
		Path storeDirectory = baseDirectory.resolve("store");
		Path logDirectory = baseDirectory.resolve("log");
		Path settingsFile = baseDirectory.resolve("acceptor.cfg");

		deleteDirectory(storeDirectory);
		deleteDirectory(logDirectory);
		log.info("Cleared FIX store and log directories for a fresh session");

		Files.createDirectories(storeDirectory);
		Files.createDirectories(logDirectory);
		Files.writeString(settingsFile, buildSettings(storeDirectory, logDirectory), StandardCharsets.UTF_8);

		SessionSettings sessionSettings = new SessionSettings(settingsFile.toString());
		MessageStoreFactory messageStoreFactory = new FileStoreFactory(sessionSettings);
		FileLogFactory logFactory = new FileLogFactory(sessionSettings);
		MessageFactory messageFactory = new DefaultMessageFactory();

		acceptor = new SocketAcceptor(this, messageStoreFactory, sessionSettings, logFactory, messageFactory);
		acceptor.start();
		log.info("FIX acceptor started with {} session(s)", properties.getSessions().size());
	}

	private void deleteDirectory(Path directory) throws IOException {
		if (Files.exists(directory)) {
			try (var entries = Files.walk(directory)) {
				entries.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							log.warn("Could not delete FIX file: {}", path, e);
						}
					});
			}
		}
	}

	private String buildSettings(Path storeDirectory, Path logDirectory) {
		StringBuilder settings = new StringBuilder();
		settings.append("[DEFAULT]\n");
		settings.append("ConnectionType=acceptor\n");
		settings.append("FileStorePath=%s\n".formatted(storeDirectory.toAbsolutePath()));
		settings.append("FileLogPath=%s\n".formatted(logDirectory.toAbsolutePath()));
		settings.append("StartTime=00:00:00\n");
		settings.append("EndTime=23:59:59\n");
		settings.append("UseDataDictionary=N\n");

		for (FixAcceptorProperties.Session session : properties.getSessions()) {
			settings.append("\n[SESSION]\n");
			settings.append("BeginString=%s\n".formatted(session.getBeginString()));
			settings.append("SenderCompID=%s\n".formatted(session.getSenderCompId()));
			settings.append("TargetCompID=%s\n".formatted(session.getTargetCompId()));
			settings.append("SocketAcceptPort=%d\n".formatted(session.getPort()));
			settings.append("HeartBtInt=%d\n".formatted(session.getHeartbeatIntervalSeconds()));
		}

		return settings.toString();
	}

	@Override
	public void destroy() {
		orderFlows.values().forEach(OrderFlow::cancel);
		orderFlows.clear();

		SocketAcceptor currentAcceptor = acceptor;
		if (currentAcceptor != null) {
			currentAcceptor.stop();
			acceptor = null;
		}

		scheduler.shutdownNow();
	}

	@Override
	public void onCreate(SessionID sessionId) {
		log.info("FIX session created: {}", sessionId);
	}

	@Override
	public void onLogon(SessionID sessionId) {
		log.info("FIX session logon: {}", sessionId);
		FixAcceptorProperties.Session session = findSession(sessionId);
		if (session == null) {
			log.warn("Ignoring logon for unknown session {}", sessionId);
			return;
		}
		orderFlows.computeIfAbsent(sessionId, ignored -> startOrderFlow(sessionId, session));
	}

	@Override
	public void onLogout(SessionID sessionId) {
		log.info("FIX session logout: {}", sessionId);
		OrderFlow orderFlow = orderFlows.remove(sessionId);
		if (orderFlow != null) {
			orderFlow.cancel();
		}
	}

	@Override
	public void toAdmin(Message message, SessionID sessionId) {
		log.debug("To admin {}: {}", sessionId, message);
	}

	@Override
	public void fromAdmin(Message message, SessionID sessionId)
		throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
		log.debug("From admin {}: {}", sessionId, message);
	}

	@Override
	public void toApp(Message message, SessionID sessionId) {
		log.debug("To app {}: {}", sessionId, message);
	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
		throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		log.info("From app {}: {}", sessionId, message);
	}

	private FixAcceptorProperties.Session findSession(SessionID sessionId) {
		return properties.getSessions().stream()
			.filter(session -> Objects.equals(session.getBeginString(), sessionId.getBeginString()))
			.filter(session -> Objects.equals(session.getSenderCompId(), sessionId.getSenderCompID()))
			.filter(session -> Objects.equals(session.getTargetCompId(), sessionId.getTargetCompID()))
			.findFirst()
			.orElse(null);
	}

	private OrderFlow startOrderFlow(SessionID sessionId, FixAcceptorProperties.Session session) {
		OrderFlow orderFlow = new OrderFlow(sessionId);
		long intervalSeconds = Math.max(1, session.getSendIntervalSeconds());
		long totalRuns = Math.max(1L, session.getSendTotalOrders());
		AtomicReference<ScheduledFuture<?>> futureReference = new AtomicReference<>();

		ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
			int currentOrderNumber = orderFlow.sentCount.incrementAndGet();
			if (currentOrderNumber > totalRuns) {
				ScheduledFuture<?> scheduledFuture = futureReference.get();
				if (scheduledFuture != null) {
					scheduledFuture.cancel(false);
				}
				orderFlows.remove(sessionId);
				return;
			}

			try {
				sendOrder(sessionId, session, currentOrderNumber);
				log.info("Sent FIX order {} to {}", currentOrderNumber, sessionId);
			} catch (Exception exception) {
				log.error("Failed to send FIX order {} to {}", currentOrderNumber, sessionId, exception);
			}

			if (currentOrderNumber >= totalRuns) {
				ScheduledFuture<?> scheduledFuture = futureReference.get();
				if (scheduledFuture != null) {
					scheduledFuture.cancel(false);
				}
				orderFlows.remove(sessionId);
			}
		}, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

		futureReference.set(future);
		orderFlow.future.set(future);
		return orderFlow;
	}

	private void sendOrder(SessionID sessionId, FixAcceptorProperties.Session session, int currentOrderNumber) throws Exception {
		String clOrdId = sessionId.getSenderCompID() + "-" + sessionId.getTargetCompID() + "-" + currentOrderNumber;
		NewOrderSingle order = new NewOrderSingle(
			new ClOrdID(clOrdId),
			new Side(resolveSide(session.getSide())),
			new TransactTime(LocalDateTime.now(ZoneId.systemDefault())),
			new OrdType(OrdType.MARKET));
		order.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PUBLIC_BROKER_INTERVENTION_OK));
		order.set(new Symbol(session.getSymbol()));
		order.set(new OrderQty(session.getQuantity()));

		Session.sendToTarget(order, sessionId);
	}

	private char resolveSide(String configuredSide) {
		if (configuredSide == null) {
			return Side.BUY;
		}
		return switch (configuredSide.trim().toUpperCase()) {
			case "SELL" -> Side.SELL;
			case "BUY" -> Side.BUY;
			default -> Side.BUY;
		};
	}

	private static final class OrderFlow {

		private final SessionID sessionId;
		private final AtomicInteger sentCount = new AtomicInteger();
		private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();

		private OrderFlow(SessionID sessionId) {
			this.sessionId = sessionId;
		}

		private void cancel() {
			ScheduledFuture<?> scheduledFuture = future.getAndSet(null);
			if (scheduledFuture != null) {
				scheduledFuture.cancel(false);
			}
		}
	}
}