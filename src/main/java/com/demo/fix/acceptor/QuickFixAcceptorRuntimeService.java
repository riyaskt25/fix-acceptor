package com.demo.fix.acceptor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import quickfix.Application;
import quickfix.ConfigError;
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
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import quickfix.UnsupportedMessageType;

@Component
@ConditionalOnProperty(prefix = "fix", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuickFixAcceptorRuntimeService extends MessageCracker implements ApplicationRunner, DisposableBean, Application {

	private static final Logger log = LoggerFactory.getLogger(QuickFixAcceptorRuntimeService.class);

	private final FixAcceptorProperties properties;
	private final FixSessionRegistry sessionRegistry;

	private volatile SocketAcceptor acceptor;

	public QuickFixAcceptorRuntimeService(
		FixAcceptorProperties properties,
		FixSessionRegistry sessionRegistry) {
		this.properties = properties;
		this.sessionRegistry = sessionRegistry;
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

		SessionSettings sessionSettings = resolveSessionSettings();
		sessionRegistry.refreshFromSessionSettings(sessionSettings);
		if (properties.getRuntime().isCleanupOnStartup()) {
			cleanupRuntimeDirectories(sessionSettings);
		} else {
			log.info("Runtime cleanup on startup disabled");
		}

		MessageStoreFactory messageStoreFactory = new FileStoreFactory(sessionSettings);
		FileLogFactory logFactory = new FileLogFactory(sessionSettings);
		MessageFactory messageFactory = new DefaultMessageFactory();

		acceptor = new SocketAcceptor(this, messageStoreFactory, sessionSettings, logFactory, messageFactory);
		acceptor.start();
		log.info("FIX acceptor started with {} session(s)", sessionRegistry.size());
		if (properties.getRuntime().isDiagnosticsEnabled()) {
			logStartupClientDiagnostics();
		} else {
			log.info("Startup client diagnostics disabled by configuration");
		}
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
		Set<Integer> uniquePorts = sessionRegistry.uniqueSocketAcceptPorts();
		log.info("Startup diagnostics will run for {} unique port(s)", uniquePorts.size());
		for (Integer port : uniquePorts) {
			WindowsFixDiagnosticsUtil.logConnectionsForPort(port, log);
		}
	}

	private SessionSettings resolveSessionSettings() throws Exception {
		FixAcceptorProperties.Settings settingsProperties = properties.getSettings();
		Path externalPath = Path.of(settingsProperties.getExternalFile());
		if (Files.exists(externalPath) && Files.isRegularFile(externalPath)) {
			log.info("Loading FIX settings from external file: {}", externalPath.toAbsolutePath());
			return new SessionSettings(externalPath.toString());
		}

		String classpathFallback = settingsProperties.getClasspathFallback();
		ClassPathResource classPathResource = new ClassPathResource(classpathFallback);
		if (classPathResource.exists()) {
			log.info("External settings file not found at {}. Loading classpath fallback: {}",
				externalPath.toAbsolutePath(),
				classpathFallback);
			try (InputStream inputStream = classPathResource.getInputStream()) {
				return new SessionSettings(inputStream);
			}
		}

		throw new IllegalStateException(
			"No settings.cfg found. Checked external path " + externalPath.toAbsolutePath() +
			" and classpath fallback " + classpathFallback);
	}

	private void cleanupRuntimeDirectories(SessionSettings settings) throws ConfigError {
		Set<String> storePaths = java.util.concurrent.ConcurrentHashMap.newKeySet();
		Set<String> logPaths = java.util.concurrent.ConcurrentHashMap.newKeySet();
		collectGlobalPath(settings, "FileStorePath", storePaths);
		collectGlobalPath(settings, "FileLogPath", logPaths);

		Iterator<SessionID> sections = settings.sectionIterator();
		while (sections.hasNext()) {
			SessionID sessionId = sections.next();
			collectSessionPath(settings, sessionId, "FileStorePath", storePaths);
			collectSessionPath(settings, sessionId, "FileLogPath", logPaths);
		}

		for (String storePath : storePaths) {
			deleteDirectory(Path.of(storePath));
		}
		for (String logPath : logPaths) {
			deleteDirectory(Path.of(logPath));
		}
		log.info("Runtime cleanup complete: storePaths={}, logPaths={}", storePaths.size(), logPaths.size());
	}

	private void collectGlobalPath(SessionSettings settings, String key, Set<String> sink) throws ConfigError {
		if (settings.isSetting(key)) {
			sink.add(settings.getString(key));
		}
	}

	private void collectSessionPath(SessionSettings settings, SessionID sessionId, String key, Set<String> sink) throws ConfigError {
		if (settings.isSetting(sessionId, key)) {
			sink.add(settings.getString(sessionId, key));
		}
	}

	private void deleteDirectory(Path directory) {
		log.info("Deleting directory if it exists: {}", directory);
		if (!Files.exists(directory)) {
			log.info("Directory does not exist, skip cleanup: {}", directory);
			return;
		}
		try (var entries = Files.walk(directory)) {
			entries.sorted(Comparator.reverseOrder())
				.forEach(path -> {
					try {
						Files.delete(path);
					} catch (Exception exception) {
						log.warn("Could not delete FIX file: {}", path, exception);
					}
				});
			log.info("Directory cleanup completed: {}", directory);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not clean directory " + directory, exception);
		}
	}
}
