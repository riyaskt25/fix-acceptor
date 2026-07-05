package com.demo.fix.acceptor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import quickfix.SessionID;

@Service
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageService {

	private final FixSessionRegistry sessionRegistry;
	private final FixMessageQueueService queueService;
	private final FixRawMessageSender rawMessageSender;

	public SendMessageService(
		FixSessionRegistry sessionRegistry,
		FixMessageQueueService queueService,
		FixRawMessageSender rawMessageSender) {
		this.sessionRegistry = sessionRegistry;
		this.queueService = queueService;
		this.rawMessageSender = rawMessageSender;
	}

	public RawMessageSubmissionResult sendRawMessage(String targetCompId, String rawMessage) {
		if (rawMessage == null || rawMessage.isBlank()) {
			throw new IllegalArgumentException("rawMessage is required");
		}
		rawMessageSender.validateExecutionReport(rawMessage);

		FixAcceptorProperties.Session session = sessionRegistry.requireByTargetCompId(targetCompId);
		SessionID sessionId = sessionRegistry.toSessionId(session);
		FixMessageQueueService.QueueResult queueResult = queueService.enqueueAndTrySend(sessionId, session, rawMessage, rawMessageSender);
		return new RawMessageSubmissionResult(targetCompId, 1, queueResult.sentNow(), queueResult.pending());
	}

	public record RawMessageSubmissionResult(String targetCompId, int requested, int sentNow, int pending) {
	}
}
