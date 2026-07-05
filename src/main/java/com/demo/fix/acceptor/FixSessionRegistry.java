package com.demo.fix.acceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import quickfix.SessionID;

@Component
public class FixSessionRegistry {

	private final Map<String, FixAcceptorProperties.Session> sessionsByTargetCompId = new ConcurrentHashMap<>();

	public void refresh(Iterable<FixAcceptorProperties.Session> sessions) {
		sessionsByTargetCompId.clear();
		for (FixAcceptorProperties.Session session : sessions) {
			sessionsByTargetCompId.put(session.getTargetCompId(), session);
		}
	}

	public FixAcceptorProperties.Session requireByTargetCompId(String targetCompId) {
		FixAcceptorProperties.Session session = sessionsByTargetCompId.get(targetCompId);
		if (session == null) {
			throw new IllegalArgumentException("Unknown targetCompId: " + targetCompId);
		}
		return session;
	}

	public SessionID toSessionId(FixAcceptorProperties.Session session) {
		return new SessionID(session.getBeginString(), session.getSenderCompId(), session.getTargetCompId());
	}
}
