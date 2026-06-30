package com.demo.fix.acceptor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fix.acceptor")
public class FixAcceptorProperties {

	private boolean enabled = true;
	private List<Session> sessions = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
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
		private String symbol = "DEMO";
		private int quantity = 100;
		private String side = "BUY";

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

		public String getSymbol() {
			return symbol;
		}

		public void setSymbol(String symbol) {
			this.symbol = symbol;
		}

		public int getQuantity() {
			return quantity;
		}

		public void setQuantity(int quantity) {
			this.quantity = quantity;
		}

		public String getSide() {
			return side;
		}

		public void setSide(String side) {
			this.side = side;
		}
	}
}