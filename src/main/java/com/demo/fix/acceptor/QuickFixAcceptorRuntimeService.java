package com.demo.fix.acceptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

@Component
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuickFixAcceptorRuntimeService extends MessageCracker implements ApplicationRunner, DisposableBean, Application {

	private static final Logger log = LoggerFactory.getLogger(QuickFixAcceptorRuntimeService.class);

	private final FixAcceptorProperties properties;
	private final FixSessionRegistry sessionRegistry;
	private final FixSettingsBuilder settingsBuilder;
	private final FixRuntimeFilesManager runtimeFilesManager;
	private final FixMessageQueueService queueService;
	private final FixRawMessageSender rawMessageSender;

	private volatile SocketAcceptor acceptor;

	public QuickFixAcceptorRuntimeService(
		FixAcceptorProperties properties,
		FixSessionRegistry sessionRegistry,
		FixSettingsBuilder settingsBuilder,
		FixRuntimeFilesManager runtimeFilesManager,
		FixMessageQueueService queueService,
		FixRawMessageSender rawMessageSender) {
		this.properties = properties;
		this.sessionRegistry = sessionRegistry;
		this.settingsBuilder = settingsBuilder;
		this.runtimeFilesManager = runtimeFilesManager;
		this.queueService = queueService;
		this.rawMessageSender = rawMessageSender;
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

		sessionRegistry.refresh(properties.getSessions());
		String settings = settingsBuilder.build(properties.getSessions(), Path.of("fix-runtime/store"), Path.of("fix-runtime/log"));
		FixRuntimeFilesManager.RuntimePaths runtimePaths = runtimeFilesManager.resetRuntime(settings);

		SessionSettings sessionSettings = new SessionSettings(runtimePaths.settingsFile().toString());
		MessageStoreFactory messageStoreFactory = new FileStoreFactory(sessionSettings);
		FileLogFactory logFactory = new FileLogFactory(sessionSettings);
		MessageFactory messageFactory = new DefaultMessageFactory();

		acceptor = new SocketAcceptor(this, messageStoreFactory, sessionSettings, logFactory, messageFactory);
		acceptor.start();
		log.info("FIX acceptor started with {} session(s)", properties.getSessions().size());
		logStartupClientDiagnostics();
	}

	@Override
	public void destroy() {
		queueService.clear();
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
		queueService.drainOnLogon(sessionId, session, rawMessageSender);
	}

	@Override
	public void onLogout(SessionID sessionId) {
		log.info("FIX session logout: {}", sessionId);
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
			.filter(session -> session.getBeginString().equals(sessionId.getBeginString()))
			.filter(session -> session.getSenderCompId().equals(sessionId.getSenderCompID()))
			.filter(session -> session.getTargetCompId().equals(sessionId.getTargetCompID()))
			.findFirst()
			.orElse(null);
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
				if (!line.isEmpty()) {
					log.info("Startup diagnostics: connected client on port {} -> {}", port, line);
				}
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
}
