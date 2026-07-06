package com.demo.fix.acceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/send-message")
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageController {
	private static final Logger log = LoggerFactory.getLogger(SendMessageController.class);

	private final SendMessageService sendMessageService;

	public SendMessageController(SendMessageService sendMessageService) {
		this.sendMessageService = sendMessageService;
		log.info("Initialized SendMessageController");
	}

	@PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SendMessageService.RawMessageSubmissionResult send(
		@RequestParam String targetCompId,
		@RequestBody String rawFixMessage) {
		log.info("Received /api/send-message request: targetCompId={}, rawLength={}",
			targetCompId,
			rawFixMessage == null ? 0 : rawFixMessage.length());

		if (targetCompId == null || targetCompId.isBlank()) {
			log.warn("Rejecting send request: missing targetCompId");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCompId is required");
		}
		if (rawFixMessage == null || rawFixMessage.isBlank()) {
			log.warn("Rejecting send request: empty rawFixMessage for targetCompId={}", targetCompId);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawFixMessage is required");
		}

		SendMessageService.RawMessageSubmissionResult result = sendMessageService.sendRawMessage(targetCompId.trim(), rawFixMessage);
		log.info("Completed /api/send-message request: targetCompId={}, sentNow={}, pending={}",
			result.targetCompId(),
			result.sentNow(),
			result.pending());
		return result;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleBadRequest(IllegalArgumentException exception) {
		log.warn("Bad request error in SendMessageController: {}", exception.getMessage());
		return exception.getMessage();
	}
}
