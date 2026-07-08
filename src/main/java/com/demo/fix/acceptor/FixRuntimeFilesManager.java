package com.demo.fix.acceptor.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FixRuntimeFilesManager {

	private static final Logger log = LoggerFactory.getLogger(FixRuntimeFilesManager.class);

	public record RuntimePaths(Path storeDirectory, Path logDirectory, Path settingsFile) {
	}

	public RuntimePaths resetRuntime(String settingsContent, Path storeDirectory, Path logDirectory) throws IOException {
		log.info("Resetting FIX runtime files: settingsLength={}", settingsContent == null ? 0 : settingsContent.length());
		Path settingsFile = Path.of("fix-runtime").resolve("acceptor.cfg");

		deleteDirectory(storeDirectory);
		deleteDirectory(logDirectory);
		log.info("Cleared FIX store and log directories for a fresh session");

		Files.createDirectories(storeDirectory);
		Files.createDirectories(logDirectory);
		Files.writeString(settingsFile, settingsContent, StandardCharsets.UTF_8);
		log.info("Runtime reset complete: settingsFile={}, storeDir={}, logDir={}", settingsFile, storeDirectory, logDirectory);
		return new RuntimePaths(storeDirectory, logDirectory, settingsFile);
	}

	private void deleteDirectory(Path directory) throws IOException {
		log.info("Deleting directory if it exists: {}", directory);
		if (Files.exists(directory)) {
			try (var entries = Files.walk(directory)) {
				entries.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							log.warn("Could not delete FIX file: {}", path, e);
						}
					});
			}
			log.info("Directory cleanup completed: {}", directory);
		} else {
			log.info("Directory does not exist, skip cleanup: {}", directory);
		}
	}
}
