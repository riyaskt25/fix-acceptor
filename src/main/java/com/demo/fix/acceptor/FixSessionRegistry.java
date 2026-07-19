package com.demo.fix.acceptor;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import quickfix.ConfigError;
import quickfix.SessionID;
import quickfix.SessionSettings;

@Component
public class FixSessionRegistry {
	private static final Logger log = LoggerFactory.getLogger(FixSessionRegistry.class);

	private final Map<String, FixAcceptorProperties.Session> sessionsByTargetCompId = new ConcurrentHashMap<>();

	public void refreshFromSessionSettings(SessionSettings settings) throws ConfigError {
		log.info("Refreshing session registry from SessionSettings");
		sessionsByTargetCompId.clear();
		int count = 0;
		Iterator<SessionID> sections = settings.sectionIterator();
		while (sections.hasNext()) {
			SessionID sessionId = sections.next();
			FixAcceptorProperties.Session session = new FixAcceptorProperties.Session();
			session.setBeginString(sessionId.getBeginString());
			session.setSenderCompId(sessionId.getSenderCompID());
			session.setTargetCompId(sessionId.getTargetCompID());
			session.setSessionQualifier(sessionId.getSessionQualifier());
			session.setSocketAcceptPort(resolveSocketAcceptPort(settings, sessionId));
			if (sessionsByTargetCompId.containsKey(session.getTargetCompId())) {
				throw new IllegalStateException("Duplicate TargetCompID in settings.cfg: " + session.getTargetCompId());
			}
			sessionsByTargetCompId.put(session.getTargetCompId(), session);
			count++;
			log.info("Registered session: targetCompId={}, senderCompId={}, beginString={}, port={}",
				session.getTargetCompId(),
				session.getSenderCompId(),
				session.getBeginString(),
				session.getSocketAcceptPort());
		}
		if (count == 0) {
			throw new IllegalStateException("No [SESSION] entries found in resolved settings.cfg");
		}
		log.info("Session registry refresh from SessionSettings complete: {} session(s)", count);
	}

	public int size() {
		return sessionsByTargetCompId.size();
	}

	public Set<Integer> uniqueSocketAcceptPorts() {
		Set<Integer> uniquePorts = ConcurrentHashMap.newKeySet();
		for (FixAcceptorProperties.Session session : sessionsByTargetCompId.values()) {
			uniquePorts.add(session.getSocketAcceptPort());
		}
		return uniquePorts;
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
			session.getSocketAcceptPort());
		return session;
	}

	public SessionID toSessionId(FixAcceptorProperties.Session session) {
		SessionID sessionId;
		if (session.getSessionQualifier() == null || session.getSessionQualifier().isBlank()) {
			sessionId = new SessionID(session.getBeginString(), session.getSenderCompId(), session.getTargetCompId());
		} else {
			sessionId = new SessionID(session.getBeginString(), session.getSenderCompId(), session.getTargetCompId(), session.getSessionQualifier());
		}
		log.info("Constructed SessionID={}", sessionId);
		return sessionId;
	}

	private int resolveSocketAcceptPort(SessionSettings settings, SessionID sessionId) throws ConfigError {
		try {
			if (settings.isSetting(sessionId, "SocketAcceptPort")) {
				return settings.getInt(sessionId, "SocketAcceptPort");
			}
			if (settings.isSetting("SocketAcceptPort")) {
				return settings.getInt("SocketAcceptPort");
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Invalid SocketAcceptPort for session " + sessionId, exception);
		}
		throw new IllegalStateException("Missing SocketAcceptPort for session " + sessionId);
	}
}
