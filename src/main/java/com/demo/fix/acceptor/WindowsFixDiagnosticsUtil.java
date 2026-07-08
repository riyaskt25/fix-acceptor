package com.demo.fix.acceptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

public final class WindowsFixDiagnosticsUtil {

	private WindowsFixDiagnosticsUtil() {
	}

	public static void logConnectionsForPort(int port, Logger log) {
		log.info("Running Windows FIX diagnostics for port={}", port);
		String command = "Get-NetTCPConnection -RemotePort " + port
			+ " -State Established -ErrorAction SilentlyContinue | "
			+ "Select-Object LocalAddress,LocalPort,RemoteAddress,RemotePort,OwningProcess | "
			+ "ConvertTo-Csv -NoTypeInformation";
		ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
		try {
			Process process = processBuilder.start();
			boolean finished = process.waitFor(3, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				log.info("Startup diagnostics timed out while checking connected clients for port {}", port);
				return;
			}
			String output = readStream(process.getInputStream(), log).trim();
			if (output.isBlank()) {
				log.info("Startup diagnostics: no connected clients on port {}", port);
				return;
			}
			String[] lines = output.split("\\R");
			if (lines.length <= 1) {
				log.info("Startup diagnostics: no connected clients on port {}", port);
				return;
			}
			for (int i = 1; i < lines.length; i++) {
				String line = lines[i].trim();
				if (!line.isEmpty()) {
					log.info("Startup diagnostics: connected client on port {} -> {}", port, line);
				}
			}
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.warn("Startup diagnostics failed for port {}", port, exception);
		}
	}

	private static String readStream(InputStream inputStream, Logger log) throws IOException {
		String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		log.debug("Diagnostics process output size={} bytes", content.length());
		return content;
	}
}
