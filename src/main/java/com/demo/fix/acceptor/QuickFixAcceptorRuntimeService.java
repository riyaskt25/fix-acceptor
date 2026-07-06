package com.demo.fix.acceptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

	private volatile SocketAcceptor acceptor;

	public QuickFixAcceptorRuntimeService(
		FixAcceptorProperties properties,
		FixSessionRegistry sessionRegistry,
		FixSettingsBuilder settingsBuilder,
		FixRuntimeFilesManager runtimeFilesManager) {
		this.properties = properties;
		this.sessionRegistry = sessionRegistry;
		this.settingsBuilder = settingsBuilder;
		this.runtimeFilesManager = runtimeFilesManager;
		log.info("Initialized QuickFixAcceptorRuntimeService");
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("ApplicationRunner invoked for QuickFixAcceptorRuntimeService");
		start();
	}

	private synchronized void start() throws Exception {
		log.info("Starting FIX acceptor runtime");
		if (acceptor != null) {
			log.info("FIX acceptor runtime already started, skipping initialization");
			return;
		}
		if (properties.getSessions().isEmpty()) {
			log.error("No FIX sessions configured");
			throw new IllegalStateException("At least one FIX acceptor session must be configured");
		}

		log.info("Refreshing FIX session registry with {} session(s)", properties.getSessions().size());
		sessionRegistry.refresh(properties.getSessions());
		String settings = settingsBuilder.build(properties.getSessions(), Path.of("fix-runtime/store"), Path.of("fix-runtime/log"));
		FixRuntimeFilesManager.RuntimePaths runtimePaths = runtimeFilesManager.resetRuntime(settings);
		log.info("Runtime files prepared: settingsFile={}, storeDir={}, logDir={}",
			runtimePaths.settingsFile(),
			runtimePaths.storeDirectory(),
			runtimePaths.logDirectory());

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
		log.info("Destroying FIX acceptor runtime");
		SocketAcceptor currentAcceptor = acceptor;
		if (currentAcceptor != null) {
			log.info("Stopping FIX acceptor instance");
			currentAcceptor.stop();
			acceptor = null;
		} else {
			log.info("No FIX acceptor instance to stop");
		}
	}

	@Override
	public void onCreate(SessionID sessionId) {
		log.info("FIX session created: {}", sessionId);
	}

	@Override
	public void onLogon(SessionID sessionId) {
		log.info("FIX session logon: {}", sessionId);
	}

	@Override
	public void onLogout(SessionID sessionId) {
		log.info("FIX session logout: {}", sessionId);
	}

	@Override
	public void toAdmin(Message message, SessionID sessionId) {
		log.debug("To admin sessionId={}, message={}", sessionId, message);
	}

	@Override
	public void fromAdmin(Message message, SessionID sessionId)
		throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
		log.debug("From admin sessionId={}, message={}", sessionId, message);
	}

	@Override
	public void toApp(Message message, SessionID sessionId) {
		log.debug("To app sessionId={}, message={}", sessionId, message);
	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
		throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		log.info("From app sessionId={}, message={}", sessionId, message);
	}

	private void logStartupClientDiagnostics() {
		log.info("Running startup client diagnostics");
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			log.info("Skipping startup client diagnostics because OS is not Windows");
			return;
		}
		Set<Integer> uniquePorts = ConcurrentHashMap.newKeySet();
		for (FixAcceptorProperties.Session session : properties.getSessions()) {
			uniquePorts.add(session.getPort());
		}
		log.info("Startup diagnostics will run for {} unique port(s)", uniquePorts.size());
		for (Integer port : uniquePorts) {
			WindowsFixDiagnosticsUtil.logConnectionsForPort(port, log);
		}
	}
}
