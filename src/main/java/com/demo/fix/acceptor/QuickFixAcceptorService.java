package com.demo.fix.acceptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
	private final Map<SessionID, AtomicInteger> sentCounters = new ConcurrentHashMap<>();
	private final Map<SessionID, ConcurrentLinkedQueue<OrderRequest>> pendingOrders = new ConcurrentHashMap<>();
	private final Map<SessionID, Object> sendLocks = new ConcurrentHashMap<>();
	private final Map<String, FixAcceptorProperties.Session> sessionsByTargetCompId = new ConcurrentHashMap<>();

	private volatile SocketAcceptor acceptor;

	public QuickFixAcceptorService(FixAcceptorProperties properties) {
		this.properties = properties;
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

		sessionsByTargetCompId.clear();
		for (FixAcceptorProperties.Session configuredSession : properties.getSessions()) {
			sessionsByTargetCompId.put(configuredSession.getTargetCompId(), configuredSession);
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
		logStartupClientDiagnostics();
	}

	private void logStartupClientDiagnostics() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			return;
		}

		Set<Integer> uniquePorts = ConcurrentHashMap.newKeySet();
		for (FixAcceptorProperties.Session session : properties.getSessions()) {
			uniquePorts.add(session.getPort());
		}

		for (Integer port : uniquePorts) {
			logWindowsConnectionsForPort(port);
		}
	}

	private void logWindowsConnectionsForPort(int port) {
		String command = "Get-NetTCPConnection -RemotePort " + port
			+ " -State Established -ErrorAction SilentlyContinue | "
			+ "Select-Object LocalAddress,LocalPort,RemoteAddress,RemotePort,OwningProcess | "
			+ "ConvertTo-Csv -NoTypeInformation";
		ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);

		try {
			Process process = processBuilder.start();
			boolean finished = process.waitFor(3, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				log.info("Startup diagnostics timed out while checking connected clients for port {}", port);
				return;
			}

			String output = readStream(process.getInputStream()).trim();
			String error = readStream(process.getErrorStream()).trim();
			if (!error.isBlank()) {
				log.debug("Startup diagnostics stderr for port {}: {}", port, error);
			}

			if (output.isBlank()) {
				log.info("Startup diagnostics: no connected clients on port {}", port);
				return;
			}

			String[] lines = output.split("\\R");
			if (lines.length <= 1) {
				log.info("Startup diagnostics: no connected clients on port {}", port);
				return;
			}

			for (int i = 1; i < lines.length; i++) {
				String line = lines[i].trim();
				if (line.isEmpty()) {
					continue;
				}
				log.info("Startup diagnostics: connected client on port {} -> {}", port, line);
			}
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.warn("Startup diagnostics failed for port {}", port, exception);
		}
	}

	private String readStream(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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
		pendingOrders.clear();
		sendLocks.clear();
		sentCounters.clear();
		sessionsByTargetCompId.clear();

		SocketAcceptor currentAcceptor = acceptor;
		if (currentAcceptor != null) {
			currentAcceptor.stop();
			acceptor = null;
		}
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
		drainPendingOrders(sessionId, session);
	}

	@Override
	public void onLogout(SessionID sessionId) {
		log.info("FIX session logout: {}", sessionId);
		// Keep pending orders for this session so they can be sent on next logon.
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

	public SubmissionResult submitOrders(String targetCompId, String symbol, Integer quantity, String side, int count) {
		FixAcceptorProperties.Session session = sessionsByTargetCompId.get(targetCompId);
		if (session == null) {
			throw new IllegalArgumentException("Unknown targetCompId: " + targetCompId);
		}

		int safeCount = Math.max(1, count);
		String resolvedSymbol = symbol == null || symbol.isBlank() ? session.getSymbol() : symbol;
		int resolvedQuantity = quantity == null || quantity <= 0 ? session.getQuantity() : quantity;
		String resolvedSide = side == null || side.isBlank() ? session.getSide() : side;

		SessionID sessionId = new SessionID(session.getBeginString(), session.getSenderCompId(), session.getTargetCompId());
		ConcurrentLinkedQueue<OrderRequest> queue = pendingOrders.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedQueue<>());
		for (int i = 0; i < safeCount; i++) {
			queue.add(new OrderRequest(resolvedSymbol, resolvedQuantity, resolvedSide));
		}

		int sentNow = drainPendingOrders(sessionId, session);
		int pending = queue.size();
		return new SubmissionResult(targetCompId, safeCount, sentNow, pending);
	}

	private int drainPendingOrders(SessionID sessionId, FixAcceptorProperties.Session session) {
		Object sendLock = sendLocks.computeIfAbsent(sessionId, ignored -> new Object());
		ConcurrentLinkedQueue<OrderRequest> queue = pendingOrders.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedQueue<>());
		Session activeSession = Session.lookupSession(sessionId);
		if (activeSession == null || !activeSession.isLoggedOn()) {
			return 0;
		}

		List<OrderRequest> failedOrders = new ArrayList<>();
		int sent = 0;
		synchronized (sendLock) {
			OrderRequest orderRequest;
			while ((orderRequest = queue.poll()) != null) {
				try {
					int nextOrderNumber = sentCounters.computeIfAbsent(sessionId, ignored -> new AtomicInteger(0)).incrementAndGet();
					sendOrder(sessionId, session, nextOrderNumber, orderRequest);
					sent++;
					log.info("Sent FIX order {} to {}", nextOrderNumber, sessionId);
				} catch (Exception exception) {
					failedOrders.add(orderRequest);
					log.error("Failed to send FIX order to {}", sessionId, exception);
				}
			}
			for (OrderRequest failedOrder : failedOrders) {
				queue.add(failedOrder);
			}
		}
		return sent;
	}

	private void sendOrder(SessionID sessionId, FixAcceptorProperties.Session session, int currentOrderNumber, OrderRequest orderRequest) throws Exception {
		String clOrdId = sessionId.getSenderCompID() + "-" + sessionId.getTargetCompID() + "-" + currentOrderNumber;
		NewOrderSingle order = new NewOrderSingle(
			new ClOrdID(clOrdId),
			new Side(resolveSide(orderRequest.side())),
			new TransactTime(LocalDateTime.now(ZoneId.systemDefault())),
			new OrdType(OrdType.MARKET));
		order.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PUBLIC_BROKER_INTERVENTION_OK));
		order.set(new Symbol(orderRequest.symbol()));
		order.set(new OrderQty(orderRequest.quantity()));

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

	private record OrderRequest(String symbol, int quantity, String side) {
	}

	public record SubmissionResult(String targetCompId, int requested, int sentNow, int pending) {
	}
}