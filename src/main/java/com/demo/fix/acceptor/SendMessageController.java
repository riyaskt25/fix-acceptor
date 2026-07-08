package com.demo.fix.acceptor.api;

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

import com.demo.fix.acceptor.application.SendMessageService;

@RestController
@RequestMapping("/api/send-message")
@ConditionalOnProperty(prefix = "fix", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageController {
	private static final Logger log = LoggerFactory.getLogger(SendMessageController.class);

	private final SendMessageService sendMessageService;

	public SendMessageController(SendMessageService sendMessageService) {
		this.sendMessageService = sendMessageService;
		log.info("Initialized SendMessageController");
	}

	@PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SendMessageService.RawMessageSubmissionResult send(
		@RequestParam String initiatorId,
		@RequestBody String fixMessage) {
		log.info("Received raw FIX send request: initiatorId={}, rawLength={}", initiatorId, fixMessage == null ? 0 : fixMessage.length());

		if (initiatorId == null || initiatorId.isBlank()) {
			log.warn("Rejecting raw FIX send request: missing initiatorId");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initiatorId is required");
		}
		if (fixMessage == null || fixMessage.isBlank()) {
			log.warn("Rejecting raw FIX send request: empty FIX message for initiatorId={}", initiatorId);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fixMessage is required");
		}

		SendMessageService.RawMessageSubmissionResult result = sendMessageService.sendRawMessage(initiatorId.trim(), fixMessage);
		log.info("Completed raw FIX send request: initiatorId={}, sentNow={}, sent={}, sessionId={}",
			result.initiatorId(),
			result.sentNow(),
			result.sent(),
			result.sessionId());
		return result;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleBadRequest(IllegalArgumentException exception) {
		log.warn("Bad request error in SendMessageController: {}", exception.getMessage());
		return exception.getMessage();
	}
}
