package com.demo.fix.acceptor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fix")
public class FixAcceptorProperties {

	private boolean enabled = true;
	private Runtime runtime = new Runtime();
	private List<Session> sessions = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
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

	public static class Runtime {

		private String storeDirectory = "fix-runtime/store";
		private String logDirectory = "fix-runtime/log";
		private boolean diagnosticsEnabled = true;

		public String getStoreDirectory() {
			return storeDirectory;
		}

		public void setStoreDirectory(String storeDirectory) {
			this.storeDirectory = storeDirectory;
		}

		public String getLogDirectory() {
			return logDirectory;
		}

		public void setLogDirectory(String logDirectory) {
			this.logDirectory = logDirectory;
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
		private int heartbeatInterval = 30;
		private String startTime = "00:00:00";
		private String endTime = "23:59:59";
		private String timeZone = "UTC";
		private String dataDictionary = "";
		private boolean persistMessages = true;
		private boolean resetOnLogon = false;
		private boolean resetOnLogout = false;
		private boolean resetOnDisconnect = false;
		private boolean validateSequenceNumbers = true;
		private boolean validateIncomingMessage = true;
		private boolean validateFieldsHaveValues = true;
		private boolean checkCompId = true;
		private boolean checkLatency = false;
		private int maxLatency = 120;
		private String fileStorePath = "fix-runtime/store";
		private String fileLogPath = "fix-runtime/log";
		private boolean useDataDictionary = false;

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

		public int getHeartbeatInterval() {
			return heartbeatInterval;
		}

		public void setHeartbeatInterval(int heartbeatInterval) {
			this.heartbeatInterval = heartbeatInterval;
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

		public String getTimeZone() {
			return timeZone;
		}

		public void setTimeZone(String timeZone) {
			this.timeZone = timeZone;
		}

		public String getDataDictionary() {
			return dataDictionary;
		}

		public void setDataDictionary(String dataDictionary) {
			this.dataDictionary = dataDictionary;
		}

		public boolean isPersistMessages() {
			return persistMessages;
		}

		public void setPersistMessages(boolean persistMessages) {
			this.persistMessages = persistMessages;
		}

		public boolean isResetOnLogon() {
			return resetOnLogon;
		}

		public void setResetOnLogon(boolean resetOnLogon) {
			this.resetOnLogon = resetOnLogon;
		}

		public boolean isResetOnLogout() {
			return resetOnLogout;
		}

		public void setResetOnLogout(boolean resetOnLogout) {
			this.resetOnLogout = resetOnLogout;
		}

		public boolean isResetOnDisconnect() {
			return resetOnDisconnect;
		}

		public void setResetOnDisconnect(boolean resetOnDisconnect) {
			this.resetOnDisconnect = resetOnDisconnect;
		}

		public boolean isValidateSequenceNumbers() {
			return validateSequenceNumbers;
		}

		public void setValidateSequenceNumbers(boolean validateSequenceNumbers) {
			this.validateSequenceNumbers = validateSequenceNumbers;
		}

		public boolean isValidateIncomingMessage() {
			return validateIncomingMessage;
		}

		public void setValidateIncomingMessage(boolean validateIncomingMessage) {
			this.validateIncomingMessage = validateIncomingMessage;
		}

		public boolean isValidateFieldsHaveValues() {
			return validateFieldsHaveValues;
		}

		public void setValidateFieldsHaveValues(boolean validateFieldsHaveValues) {
			this.validateFieldsHaveValues = validateFieldsHaveValues;
		}

		public boolean isCheckCompId() {
			return checkCompId;
		}

		public void setCheckCompId(boolean checkCompId) {
			this.checkCompId = checkCompId;
		}

		public boolean isCheckLatency() {
			return checkLatency;
		}

		public void setCheckLatency(boolean checkLatency) {
			this.checkLatency = checkLatency;
		}

		public int getMaxLatency() {
			return maxLatency;
		}

		public void setMaxLatency(int maxLatency) {
			this.maxLatency = maxLatency;
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

		public boolean isUseDataDictionary() {
			return useDataDictionary;
		}

		public void setUseDataDictionary(boolean useDataDictionary) {
			this.useDataDictionary = useDataDictionary;
		}
	}
}