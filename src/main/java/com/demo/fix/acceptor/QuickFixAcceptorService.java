package com.demo.fix.acceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

		Path baseDirectory = Path.of("build", "fix");
		Path storeDirectory = baseDirectory.resolve("store");
		Path logDirectory = baseDirectory.resolve("log");
		Path settingsFile = baseDirectory.resolve("acceptor.cfg");

		Files.createDirectories(storeDirectory);
		Files.createDirectories(logDirectory);
		Files.writeString(settingsFile, buildSettings(storeDirectory, logDirectory), StandardCharsets.UTF_8);

		SessionSettings sessionSettings = new SessionSettings(settingsFile.toString());
		MessageStoreFactory messageStoreFactory = new FileStoreFactory(sessionSettings);
		FileLogFactory logFactory = new FileLogFactory(sessionSettings);
		MessageFactory messageFactory = new DefaultMessageFactory();

		acceptor = new SocketAcceptor(this, messageStoreFactory, sessionSettings, logFactory, messageFactory);
		acceptor.start();
		log.info("FIX acceptor started on port {}", properties.getPort());
	}

	private String buildSettings(Path storeDirectory, Path logDirectory) {
		return """
			[DEFAULT]
			ConnectionType=acceptor
			HeartBtInt=%d
			FileStorePath=%s
			FileLogPath=%s
			StartTime=00:00:00
			EndTime=23:59:59
			UseDataDictionary=N

			[SESSION]
			BeginString=%s
			SenderCompID=%s
			TargetCompID=%s
			SocketAcceptPort=%d
			""".formatted(
				properties.getHeartbeatIntervalSeconds(),
				storeDirectory.toAbsolutePath(),
				logDirectory.toAbsolutePath(),
				properties.getBeginString(),
				properties.getSenderCompId(),
				properties.getTargetCompId(),
				properties.getPort());
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
		orderFlows.computeIfAbsent(sessionId, this::startOrderFlow);
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

	private OrderFlow startOrderFlow(SessionID sessionId) {
		OrderFlow orderFlow = new OrderFlow(sessionId);
		long intervalSeconds = Math.max(1, properties.getSendIntervalSeconds());
		long totalRuns = Math.max(1L, (properties.getSendDurationMinutes() * 60L) / intervalSeconds);
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
				sendOrder(sessionId, currentOrderNumber);
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

	private void sendOrder(SessionID sessionId, int currentOrderNumber) throws Exception {
		String clOrdId = sessionId.getSenderCompID() + "-" + sessionId.getTargetCompID() + "-" + currentOrderNumber;
		NewOrderSingle order = new NewOrderSingle(
			new ClOrdID(clOrdId),
			new Side(resolveSide(properties.getSide())),
			new TransactTime(LocalDateTime.now(ZoneId.systemDefault())),
			new OrdType(OrdType.MARKET));
		order.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PUBLIC_BROKER_INTERVENTION_OK));
		order.set(new Symbol(properties.getSymbol()));
		order.set(new OrderQty(properties.getQuantity()));

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