package com.demo.fix.acceptor;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

@Component
public class FixSettingsBuilder {

	public String build(Iterable<FixAcceptorProperties.Session> sessions, Path storeDirectory, Path logDirectory) {
		StringBuilder settings = new StringBuilder();
		settings.append("[DEFAULT]\n");
		settings.append("ConnectionType=acceptor\n");
		settings.append("FileStorePath=%s\n".formatted(storeDirectory.toAbsolutePath()));
		settings.append("FileLogPath=%s\n".formatted(logDirectory.toAbsolutePath()));
		settings.append("StartTime=00:00:00\n");
		settings.append("EndTime=23:59:59\n");
		settings.append("UseDataDictionary=N\n");

		for (FixAcceptorProperties.Session session : sessions) {
			settings.append("\n[SESSION]\n");
			settings.append("BeginString=%s\n".formatted(session.getBeginString()));
			settings.append("SenderCompID=%s\n".formatted(session.getSenderCompId()));
			settings.append("TargetCompID=%s\n".formatted(session.getTargetCompId()));
			settings.append("SocketAcceptPort=%d\n".formatted(session.getPort()));
			settings.append("HeartBtInt=%d\n".formatted(session.getHeartbeatIntervalSeconds()));
		}

		return settings.toString();
	}
}
