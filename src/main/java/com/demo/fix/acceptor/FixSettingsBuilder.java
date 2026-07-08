package com.demo.fix.acceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FixSettingsBuilder {
	private static final Logger log = LoggerFactory.getLogger(FixSettingsBuilder.class);

	public String build(FixAcceptorProperties properties) {
		log.info("Building FIX settings: storeDirectory={}, logDirectory={}",
			properties.getRuntime().getStoreDirectory(),
			properties.getRuntime().getLogDirectory());
		StringBuilder settings = new StringBuilder();
		settings.append("[DEFAULT]\n");
		settings.append("ConnectionType=acceptor\n");
		settings.append("FileStorePath=%s\n".formatted(properties.getRuntime().getStoreDirectory()));
		settings.append("FileLogPath=%s\n".formatted(properties.getRuntime().getLogDirectory()));

		FixAcceptorProperties.Session firstSession = properties.getSessions().isEmpty() ? null : properties.getSessions().get(0);
		if (firstSession != null) {
			settings.append("StartTime=%s\n".formatted(firstSession.getStartTime()));
			settings.append("EndTime=%s\n".formatted(firstSession.getEndTime()));
			settings.append("UseDataDictionary=%s\n".formatted(toYesNo(firstSession.isUseDataDictionary())));
		} else {
			settings.append("StartTime=00:00:00\n");
			settings.append("EndTime=23:59:59\n");
			settings.append("UseDataDictionary=N\n");
		}

		int sessionCount = 0;
		for (FixAcceptorProperties.Session session : properties.getSessions()) {
			settings.append("\n[SESSION]\n");
			settings.append("BeginString=%s\n".formatted(session.getBeginString()));
			settings.append("SenderCompID=%s\n".formatted(session.getSenderCompId()));
			settings.append("TargetCompID=%s\n".formatted(session.getTargetCompId()));
			if (session.getSessionQualifier() != null && !session.getSessionQualifier().isBlank()) {
				settings.append("SessionQualifier=%s\n".formatted(session.getSessionQualifier()));
			}
			settings.append("SocketAcceptPort=%d\n".formatted(session.getSocketAcceptPort()));
			settings.append("HeartBtInt=%d\n".formatted(session.getHeartbeatInterval()));
			settings.append("StartTime=%s\n".formatted(session.getStartTime()));
			settings.append("EndTime=%s\n".formatted(session.getEndTime()));
			if (session.getTimeZone() != null && !session.getTimeZone().isBlank()) {
				settings.append("TimeZone=%s\n".formatted(session.getTimeZone()));
			}
			if (session.getDataDictionary() != null && !session.getDataDictionary().isBlank()) {
				settings.append("DataDictionary=%s\n".formatted(session.getDataDictionary()));
			}
			settings.append("PersistMessages=%s\n".formatted(toYesNo(session.isPersistMessages())));
			settings.append("ResetOnLogon=%s\n".formatted(toYesNo(session.isResetOnLogon())));
			settings.append("ResetOnLogout=%s\n".formatted(toYesNo(session.isResetOnLogout())));
			settings.append("ResetOnDisconnect=%s\n".formatted(toYesNo(session.isResetOnDisconnect())));
			settings.append("ValidateSequenceNumbers=%s\n".formatted(toYesNo(session.isValidateSequenceNumbers())));
			settings.append("ValidateIncomingMessage=%s\n".formatted(toYesNo(session.isValidateIncomingMessage())));
			settings.append("ValidateFieldsHaveValues=%s\n".formatted(toYesNo(session.isValidateFieldsHaveValues())));
			settings.append("CheckCompID=%s\n".formatted(toYesNo(session.isCheckCompId())));
			settings.append("CheckLatency=%s\n".formatted(toYesNo(session.isCheckLatency())));
			settings.append("MaxLatency=%d\n".formatted(session.getMaxLatency()));
			if (session.getFileStorePath() != null && !session.getFileStorePath().isBlank()) {
				settings.append("FileStorePath=%s\n".formatted(session.getFileStorePath()));
			}
			if (session.getFileLogPath() != null && !session.getFileLogPath().isBlank()) {
				settings.append("FileLogPath=%s\n".formatted(session.getFileLogPath()));
			}
			sessionCount++;
			log.info("Added session to settings: targetCompId={}, senderCompId={}, port={}",
				session.getTargetCompId(),
				session.getSenderCompId(),
				session.getSocketAcceptPort());
		}

		log.info("FIX settings build complete with {} session(s)", sessionCount);
		return settings.toString();
	}

	private String toYesNo(boolean value) {
		return value ? "Y" : "N";
	}
}
