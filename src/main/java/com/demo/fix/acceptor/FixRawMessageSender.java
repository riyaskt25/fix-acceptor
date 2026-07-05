package com.demo.fix.acceptor;

import org.springframework.stereotype.Component;

import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

@Component
public class FixRawMessageSender {

	public void validateExecutionReport(String rawMessage) {
		try {
			Message message = new Message(rawMessage);
			String messageType = message.getHeader().getString(MsgType.FIELD);
			if (!"8".equals(messageType)) {
				throw new IllegalArgumentException("Only FIX 35=8 execution reports are supported");
			}
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("Invalid FIX execution report message", exception);
		}
	}

	public void send(FixAcceptorProperties.Session session, SessionID sessionId, String rawMessage) throws Exception {
		Message message = new Message(rawMessage);
		message.getHeader().setString(SenderCompID.FIELD, session.getSenderCompId());
		message.getHeader().setString(TargetCompID.FIELD, session.getTargetCompId());
		Session.sendToTarget(message, sessionId);
	}
}
