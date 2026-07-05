package com.demo.fix.acceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import quickfix.Session;
import quickfix.SessionID;

@Component
public class FixMessageQueueService {

	private static final Logger log = LoggerFactory.getLogger(FixMessageQueueService.class);

	private final ConcurrentHashMap<SessionID, ConcurrentLinkedQueue<String>> pendingRawMessages = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<SessionID, Object> sendLocks = new ConcurrentHashMap<>();

	public void clear() {
		pendingRawMessages.clear();
		sendLocks.clear();
	}

	public QueueResult enqueueAndTrySend(SessionID sessionId, FixAcceptorProperties.Session session, String rawMessage, FixRawMessageSender sender) {
		ConcurrentLinkedQueue<String> queue = pendingRawMessages.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedQueue<>());
		queue.add(rawMessage);
		int sentNow = drain(sessionId, session, sender);
		return new QueueResult(sentNow, queue.size());
	}

	public void drainOnLogon(SessionID sessionId, FixAcceptorProperties.Session session, FixRawMessageSender sender) {
		drain(sessionId, session, sender);
	}

	private int drain(SessionID sessionId, FixAcceptorProperties.Session session, FixRawMessageSender sender) {
		Object sendLock = sendLocks.computeIfAbsent(sessionId, ignored -> new Object());
		ConcurrentLinkedQueue<String> queue = pendingRawMessages.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedQueue<>());
		Session activeSession = Session.lookupSession(sessionId);
		if (activeSession == null || !activeSession.isLoggedOn()) {
			return 0;
		}

		int sent = 0;
		synchronized (sendLock) {
			String rawMessage;
			while ((rawMessage = queue.poll()) != null) {
				try {
					sender.send(session, sessionId, rawMessage);
					sent++;
					log.info("Sent FIX execution report to {}", sessionId);
				} catch (Exception exception) {
					queue.add(rawMessage);
					log.error("Failed to send FIX execution report to {}", sessionId, exception);
				}
			}
		}
		return sent;
	}

	public record QueueResult(int sentNow, int pending) {
	}
}
