package com.demo.fix.acceptor.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

import com.demo.fix.acceptor.FixAcceptorProperties;
import com.demo.fix.acceptor.api.NewOrderRequest;
import com.demo.fix.acceptor.infrastructure.FixRawMessageSender;
import com.demo.fix.acceptor.infrastructure.FixSessionRegistry;

@Service
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageService {
	private static final Logger log = LoggerFactory.getLogger(SendMessageService.class);

	private final FixSessionRegistry sessionRegistry;
	private final NewOrderSingleMessageBuilder newOrderSingleMessageBuilder;
	private final FixRawMessageSender rawMessageSender;

	public SendMessageService(
		FixSessionRegistry sessionRegistry,
		NewOrderSingleMessageBuilder newOrderSingleMessageBuilder,
		FixRawMessageSender rawMessageSender) {
		this.sessionRegistry = sessionRegistry;
		this.newOrderSingleMessageBuilder = newOrderSingleMessageBuilder;
		this.rawMessageSender = rawMessageSender;
		log.info("Initialized SendMessageService");
	}

	public NewOrderSubmissionResult sendNewOrder(String targetCompId, NewOrderRequest request) {
		log.info("Processing sendNewOrder: targetCompId={}, clOrdId={}, symbol={}, side={}, ordType={}, orderQty={}, price={}, stopPx={}",
			targetCompId,
			request == null ? null : request.clOrdId(),
			request == null ? null : request.symbol(),
			request == null ? null : request.side(),
			request == null ? null : request.ordType(),
			request == null ? null : request.orderQty(),
			request == null ? null : request.price(),
			request == null ? null : request.stopPx());
		if (request == null) {
			log.warn("sendNewOrder rejected: request body is null, targetCompId={}", targetCompId);
			throw new IllegalArgumentException("request body is required");
		}

		FixAcceptorProperties.Session session = sessionRegistry.requireByTargetCompId(targetCompId);
		SessionID sessionId = sessionRegistry.toSessionId(session);
		log.info("Resolved session for sendNewOrder: targetCompId={}, sessionId={}", targetCompId, sessionId);

		Session activeSession = Session.lookupSession(sessionId);
		if (activeSession == null || !activeSession.isLoggedOn()) {
			log.info("Initiator is not logged on, new order ignored: targetCompId={}, sessionId={}", targetCompId, sessionId);
			return new NewOrderSubmissionResult(targetCompId, 0, false, sessionId.toString());
		}

		try {
			Message message = newOrderSingleMessageBuilder.build(request);
			boolean sent = rawMessageSender.send(session, sessionId, message);
			if (!sent) {
				log.warn("QuickFIX did not send new order (sendToTarget=false): targetCompId={}, sessionId={}", targetCompId, sessionId);
				return new NewOrderSubmissionResult(targetCompId, 0, false, sessionId.toString());
			}
			log.info("New order sent successfully: targetCompId={}, sessionId={}, clOrdId={}, symbol={}, side={}, orderQty={}",
				targetCompId,
				sessionId,
				request.clOrdId(),
				request.symbol(),
				request.side(),
				request.orderQty());
			return new NewOrderSubmissionResult(targetCompId, 1, true, sessionId.toString());
		} catch (Exception exception) {
			log.error("New order send failed: targetCompId={}, sessionId={}, clOrdId={}, symbol={}, side={}, orderQty={}",
				targetCompId,
				sessionId,
				request.clOrdId(),
				request.symbol(),
				request.side(),
				request.orderQty(),
				exception);
			throw new IllegalArgumentException("Failed to send FIX new order", exception);
		}
	}

	public record NewOrderSubmissionResult(String targetCompId, int sentNow, boolean sent, String sessionId) {
	}
}
