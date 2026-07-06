package com.demo.fix.acceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

@Service
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageService {
	private static final Logger log = LoggerFactory.getLogger(SendMessageService.class);

	private final FixSessionRegistry sessionRegistry;
	private final ExecutionReportMessageBuilder executionReportMessageBuilder;
	private final FixRawMessageSender rawMessageSender;

	public SendMessageService(
		FixSessionRegistry sessionRegistry,
		ExecutionReportMessageBuilder executionReportMessageBuilder,
		FixRawMessageSender rawMessageSender) {
		this.sessionRegistry = sessionRegistry;
		this.executionReportMessageBuilder = executionReportMessageBuilder;
		this.rawMessageSender = rawMessageSender;
		log.info("Initialized SendMessageService");
	}

	public ExecutionReportSubmissionResult sendExecutionReport(String targetCompId, ExecutionReportRequest request) {
		log.info("Processing sendExecutionReport: targetCompId={}, clOrdId={}, execId={}, execType={}, ordStatus={}, symbol={}, side={}, orderQty={}",
			targetCompId,
			request == null ? null : request.clOrdId(),
			request == null ? null : request.execId(),
			request == null ? null : request.execType(),
			request == null ? null : request.ordStatus(),
			request == null ? null : request.symbol(),
			request == null ? null : request.side(),
			request == null ? null : request.orderQty());
		if (request == null) {
			log.warn("sendExecutionReport rejected: request body is null, targetCompId={}", targetCompId);
			throw new IllegalArgumentException("request body is required");
		}

		FixAcceptorProperties.Session session = sessionRegistry.requireByTargetCompId(targetCompId);
		SessionID sessionId = sessionRegistry.toSessionId(session);
		log.info("Resolved session for sendExecutionReport: targetCompId={}, sessionId={}", targetCompId, sessionId);

		Session activeSession = Session.lookupSession(sessionId);
		if (activeSession == null || !activeSession.isLoggedOn()) {
			log.info("Initiator is not logged on, execution report ignored: targetCompId={}, sessionId={}", targetCompId, sessionId);
			return new ExecutionReportSubmissionResult(targetCompId, 0, false, sessionId.toString());
		}

		try {
			Message message = executionReportMessageBuilder.build(request);
			boolean sent = rawMessageSender.send(session, sessionId, message);
			if (!sent) {
				log.warn("QuickFIX did not send execution report (sendToTarget=false): targetCompId={}, sessionId={}", targetCompId, sessionId);
				return new ExecutionReportSubmissionResult(targetCompId, 0, false, sessionId.toString());
			}
			log.info("Execution report sent successfully: targetCompId={}, sessionId={}, clOrdId={}, execId={}",
				targetCompId,
				sessionId,
				request.clOrdId(),
				request.execId());
			return new ExecutionReportSubmissionResult(targetCompId, 1, true, sessionId.toString());
		} catch (Exception exception) {
			log.error("Execution report send failed: targetCompId={}, sessionId={}, clOrdId={}, execId={}",
				targetCompId,
				sessionId,
				request.clOrdId(),
				request.execId(),
				exception);
			throw new IllegalArgumentException("Failed to send FIX execution report", exception);
		}
	}

	public record ExecutionReportSubmissionResult(String targetCompId, int sentNow, boolean sent, String sessionId) {
	}
}
