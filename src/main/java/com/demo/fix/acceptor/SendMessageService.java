package com.demo.fix.acceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import quickfix.Session;
import quickfix.SessionID;

@Service
@ConditionalOnProperty(prefix = "fix", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageService {
	private static final Logger log = LoggerFactory.getLogger(SendMessageService.class);

	private final FixSessionRegistry sessionRegistry;
	private final FixRawMessageSender rawMessageSender;

	public SendMessageService(
		FixSessionRegistry sessionRegistry,
		FixRawMessageSender rawMessageSender) {
		this.sessionRegistry = sessionRegistry;
		this.rawMessageSender = rawMessageSender;
		log.info("Initialized SendMessageService");
	}

	public RawMessageSubmissionResult sendRawMessage(String initiatorId, String fixMessage) {
		log.info("Processing sendRawMessage: initiatorId={}, rawLength={}", initiatorId, fixMessage == null ? 0 : fixMessage.length());
		if (initiatorId == null || initiatorId.isBlank()) {
			throw new IllegalArgumentException("initiatorId is required");
		}
		if (fixMessage == null || fixMessage.isBlank()) {
			throw new IllegalArgumentException("fixMessage is required");
		}

		FixAcceptorProperties.Session session = sessionRegistry.requireByTargetCompId(initiatorId.trim());
		SessionID sessionId = sessionRegistry.toSessionId(session);
		log.info("Resolved session for raw FIX send: initiatorId={}, sessionId={}", initiatorId, sessionId);

		Session activeSession = Session.lookupSession(sessionId);
		if (activeSession == null || !activeSession.isLoggedOn()) {
			log.info("Initiator is not logged on, raw FIX message ignored: initiatorId={}, sessionId={}", initiatorId, sessionId);
			return new RawMessageSubmissionResult(initiatorId, 0, false, sessionId.toString());
		}

		try {
			String normalizedMessage = normalizeFixMessage(fixMessage);
			boolean sent = rawMessageSender.send(session, sessionId, normalizedMessage);
			if (!sent) {
				log.warn("QuickFIX did not send raw message (sendToTarget=false): initiatorId={}, sessionId={}", initiatorId, sessionId);
				return new RawMessageSubmissionResult(initiatorId, 0, false, sessionId.toString());
			}
			log.info("Raw FIX message sent successfully: initiatorId={}, sessionId={}", initiatorId, sessionId);
			return new RawMessageSubmissionResult(initiatorId, 1, true, sessionId.toString());
		} catch (Exception exception) {
			log.error("Raw FIX send failed: initiatorId={}, sessionId={}", initiatorId, sessionId, exception);
			throw new IllegalArgumentException("Failed to send raw FIX message", exception);
		}
	}

	private String normalizeFixMessage(String fixMessage) {
		if (fixMessage.indexOf('\u0001') >= 0) {
			return fixMessage;
		}
		if (fixMessage.indexOf('|') >= 0) {
			return fixMessage.replace('|', '\u0001');
		}
		return fixMessage;
	}

	public record RawMessageSubmissionResult(String initiatorId, int sentNow, boolean sent, String sessionId) {
	}

}
