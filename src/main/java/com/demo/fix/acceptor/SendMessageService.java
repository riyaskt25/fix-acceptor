package com.demo.fix.acceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import quickfix.Session;
import quickfix.SessionID;

@Service
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
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

	public RawMessageSubmissionResult sendRawMessage(String targetCompId, String rawMessage) {
		log.info("Processing sendRawMessage: targetCompId={}, rawLength={}", targetCompId, rawMessage == null ? 0 : rawMessage.length());
		if (rawMessage == null || rawMessage.isBlank()) {
			log.warn("sendRawMessage rejected: rawMessage is empty, targetCompId={}", targetCompId);
			throw new IllegalArgumentException("rawMessage is required");
		}
        
		FixAcceptorProperties.Session session = sessionRegistry.requireByTargetCompId(targetCompId);
		SessionID sessionId = sessionRegistry.toSessionId(session);
		log.info("Resolved session for sendRawMessage: targetCompId={}, sessionId={}", targetCompId, sessionId);

		Session activeSession = Session.lookupSession(sessionId);
		if (activeSession == null || !activeSession.isLoggedOn()) {
			log.info("Initiator is not logged on, message ignored: targetCompId={}, sessionId={}", targetCompId, sessionId);
			return new RawMessageSubmissionResult(targetCompId, 1, 0, 0);
		}

		try {
			boolean sent = rawMessageSender.send(session, sessionId, rawMessage);
			if (!sent) {
				log.warn("QuickFIX did not send message (sendToTarget=false): targetCompId={}, sessionId={}", targetCompId, sessionId);
				return new RawMessageSubmissionResult(targetCompId, 1, 0, 0);
			}
			log.info("Message sent successfully: targetCompId={}, sessionId={}", targetCompId, sessionId);
			return new RawMessageSubmissionResult(targetCompId, 1, 1, 0);
		} catch (Exception exception) {
			log.error("Message send failed: targetCompId={}, sessionId={}", targetCompId, sessionId, exception);
			throw new IllegalArgumentException("Failed to send FIX message", exception);
		}
	}

	public record RawMessageSubmissionResult(String targetCompId, int requested, int sentNow, int pending) {
	}
}
