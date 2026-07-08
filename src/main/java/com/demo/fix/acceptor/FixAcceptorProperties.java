package com.demo.fix.acceptor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fix.acceptor")
public class FixAcceptorProperties {

	private boolean enabled = true;
	private String connectionType = "acceptor";
	private String fileStorePath = "fix-runtime/store";
	private String fileLogPath = "fix-runtime/log";
	private String startTime = "00:00:00";
	private String endTime = "23:59:59";
	private String useDataDictionary = "N";
	private List<Session> sessions = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getConnectionType() {
		return connectionType;
	}

	public void setConnectionType(String connectionType) {
		this.connectionType = connectionType;
	}

	public String getFileStorePath() {
		return fileStorePath;
	}

	public void setFileStorePath(String fileStorePath) {
		this.fileStorePath = fileStorePath;
	}

	public String getFileLogPath() {
		return fileLogPath;
	}

	public void setFileLogPath(String fileLogPath) {
		this.fileLogPath = fileLogPath;
	}

	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	public String getEndTime() {
		return endTime;
	}

	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}

	public String getUseDataDictionary() {
		return useDataDictionary;
	}

	public void setUseDataDictionary(String useDataDictionary) {
		this.useDataDictionary = useDataDictionary;
	}

	public List<Session> getSessions() {
		return sessions;
	}

	public void setSessions(List<Session> sessions) {
		this.sessions = sessions == null ? new ArrayList<>() : sessions;
	}

	public static class Session {

		private int port = 9878;
		private String beginString = "FIX.4.4";
		private String senderCompId = "ACCEPTOR";
		private String targetCompId = "INITIATOR";
		private int heartbeatIntervalSeconds = 30;

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

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

		public int getHeartbeatIntervalSeconds() {
			return heartbeatIntervalSeconds;
		}

		public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
			this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
		}
	}
}