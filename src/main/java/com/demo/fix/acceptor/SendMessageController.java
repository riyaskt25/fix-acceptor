package com.demo.fix.acceptor;

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

	private final SendMessageService sendMessageService;

	public SendMessageController(SendMessageService sendMessageService) {
		this.sendMessageService = sendMessageService;
	}

	@PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SendMessageService.RawMessageSubmissionResult send(
		@RequestParam String targetCompId,
		@RequestBody String rawFixMessage) {

		if (targetCompId == null || targetCompId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCompId is required");
		}
		if (rawFixMessage == null || rawFixMessage.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawFixMessage is required");
		}

		return sendMessageService.sendRawMessage(targetCompId.trim(), rawFixMessage);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleBadRequest(IllegalArgumentException exception) {
		return exception.getMessage();
	}
}
