package com.demo.fix.acceptor.infrastructure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.demo.fix.acceptor.FixAcceptorProperties;

import quickfix.SessionID;

@Component
public class FixSessionRegistry {
	private static final Logger log = LoggerFactory.getLogger(FixSessionRegistry.class);

	private final Map<String, FixAcceptorProperties.Session> sessionsByTargetCompId = new ConcurrentHashMap<>();

	public void refresh(Iterable<FixAcceptorProperties.Session> sessions) {
		log.info("Refreshing session registry");
		sessionsByTargetCompId.clear();
		int count = 0;
		for (FixAcceptorProperties.Session session : sessions) {
			sessionsByTargetCompId.put(session.getTargetCompId(), session);
			count++;
			log.info("Registered session: targetCompId={}, senderCompId={}, beginString={}, port={}",
				session.getTargetCompId(),
				session.getSenderCompId(),
				session.getBeginString(),
				session.getPort());
		}
		log.info("Session registry refresh complete: {} session(s)", count);
	}

	public FixAcceptorProperties.Session requireByTargetCompId(String targetCompId) {
		log.info("Looking up session by targetCompId={}", targetCompId);
		FixAcceptorProperties.Session session = sessionsByTargetCompId.get(targetCompId);
		if (session == null) {
			log.warn("No session found for targetCompId={}", targetCompId);
			throw new IllegalArgumentException("Unknown targetCompId: " + targetCompId);
		}
		log.info("Session resolved for targetCompId={}: senderCompId={}, beginString={}, port={}",
			targetCompId,
			session.getSenderCompId(),
			session.getBeginString(),
			session.getPort());
		return session;
	}

	public SessionID toSessionId(FixAcceptorProperties.Session session) {
		SessionID sessionId = new SessionID(session.getBeginString(), session.getSenderCompId(), session.getTargetCompId());
		log.info("Constructed SessionID={}", sessionId);
		return sessionId;
	}
}
