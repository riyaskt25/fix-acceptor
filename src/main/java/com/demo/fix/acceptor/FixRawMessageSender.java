package com.demo.fix.acceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

@Component
public class FixRawMessageSender {
	private static final Logger log = LoggerFactory.getLogger(FixRawMessageSender.class);

    public boolean send(FixAcceptorProperties.Session session, SessionID sessionId, String rawMessage) throws Exception {
        log.info("Preparing FIX message send: sessionId={}, senderCompId={}, targetCompId={}, rawLength={}",
            sessionId,
            session.getSenderCompId(),
            session.getTargetCompId(),
            rawMessage == null ? 0 : rawMessage.length());
        Message message = new Message(rawMessage);
        message.getHeader().setString(SenderCompID.FIELD, session.getSenderCompId());
        message.getHeader().setString(TargetCompID.FIELD, session.getTargetCompId());
        log.debug("Sending FIX message to target: sessionId={}, message={}", sessionId, message);
        boolean sent = Session.sendToTarget(message, sessionId);
        log.info("FIX send result: sessionId={}, sent={}", sessionId, sent);
        return sent;
    }

    public boolean send(FixAcceptorProperties.Session session, SessionID sessionId, Message message) throws Exception {
        log.info("Preparing FIX typed message send: sessionId={}, senderCompId={}, targetCompId={}, messageType={}",
            sessionId,
            session.getSenderCompId(),
            session.getTargetCompId(),
            message.getHeader().getString(35));
        message.getHeader().setString(SenderCompID.FIELD, session.getSenderCompId());
        message.getHeader().setString(TargetCompID.FIELD, session.getTargetCompId());
        log.debug("Sending FIX typed message to target: sessionId={}, message={}", sessionId, message);
        boolean sent = Session.sendToTarget(message, sessionId);
        log.info("FIX typed send result: sessionId={}, sent={}", sessionId, sent);
        return sent;
    }
}
