package com.demo.fix.acceptor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fix")
public class FixAcceptorProperties {

	private boolean enabled = true;
	private Settings settings = new Settings();
	private Runtime runtime = new Runtime();
	private List<Session> sessions = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Settings getSettings() {
		return settings;
	}

	public void setSettings(Settings settings) {
		this.settings = settings == null ? new Settings() : settings;
	}

	public Runtime getRuntime() {
		return runtime;
	}

	public void setRuntime(Runtime runtime) {
		this.runtime = runtime == null ? new Runtime() : runtime;
	}

	public List<Session> getSessions() {
		return sessions;
	}

	public void setSessions(List<Session> sessions) {
		this.sessions = sessions == null ? new ArrayList<>() : sessions;
	}

	public static class Settings {

		private String externalFile = "acceptor.cfg";
		private String classpathFallback = "fix/acceptor.cfg";

		public String getExternalFile() {
			return externalFile;
		}

		public void setExternalFile(String externalFile) {
			this.externalFile = externalFile;
		}

		public String getClasspathFallback() {
			return classpathFallback;
		}

		public void setClasspathFallback(String classpathFallback) {
			this.classpathFallback = classpathFallback;
		}
	}

	public static class Runtime {

		private boolean cleanupOnStartup = true;
		private boolean diagnosticsEnabled = true;

		public boolean isCleanupOnStartup() {
			return cleanupOnStartup;
		}

		public void setCleanupOnStartup(boolean cleanupOnStartup) {
			this.cleanupOnStartup = cleanupOnStartup;
		}

		public boolean isDiagnosticsEnabled() {
			return diagnosticsEnabled;
		}

		public void setDiagnosticsEnabled(boolean diagnosticsEnabled) {
			this.diagnosticsEnabled = diagnosticsEnabled;
		}
	}

	public static class Session {

		private String beginString = "FIX.4.4";
		private String senderCompId = "ACCEPTOR";
		private String targetCompId = "INITIATOR";
		private String sessionQualifier = "";
		private int socketAcceptPort = 9878;

		public String getBeginString() {
			return beginString;
		}

		public void setBeginString(String beginString) {
			this.beginString = beginString;
		}

		public String getSenderCompId() {
			return senderCompId;
		}

		public void setSenderCompId(String senderCompId) {
			this.senderCompId = senderCompId;
		}

		public String getTargetCompId() {
			return targetCompId;
		}

		public void setTargetCompId(String targetCompId) {
			this.targetCompId = targetCompId;
		}

		public String getSessionQualifier() {
			return sessionQualifier;
		}

		public void setSessionQualifier(String sessionQualifier) {
			this.sessionQualifier = sessionQualifier;
		}

		public int getSocketAcceptPort() {
			return socketAcceptPort;
		}

		public void setSocketAcceptPort(int socketAcceptPort) {
			this.socketAcceptPort = socketAcceptPort;
		}
	}
}