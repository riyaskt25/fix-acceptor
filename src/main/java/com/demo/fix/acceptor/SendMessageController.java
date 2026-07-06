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
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendMessageController {
	private static final Logger log = LoggerFactory.getLogger(SendMessageController.class);

	private final SendMessageService sendMessageService;

	public SendMessageController(SendMessageService sendMessageService) {
		this.sendMessageService = sendMessageService;
		log.info("Initialized SendMessageController");
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SendMessageService.NewOrderSubmissionResult send(
		@RequestParam String targetCompId,
		@RequestBody NewOrderRequest request) {
		log.info("Received /api/send-message request: targetCompId={}, rawLength={}",
			targetCompId,
			request == null ? 0 : 1);

		if (targetCompId == null || targetCompId.isBlank()) {
			log.warn("Rejecting send request: missing targetCompId");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCompId is required");
		}
		if (request == null) {
			log.warn("Rejecting send request: empty request body for targetCompId={}", targetCompId);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
		}

		log.info("Request summary: targetCompId={}, clOrdId={}, symbol={}, side={}, ordType={}, orderQty={}, price={}, stopPx={}, parties={}, extraFields={}",
			targetCompId,
			request.clOrdId(),
			request.symbol(),
			request.side(),
			request.ordType(),
			request.orderQty(),
			request.price(),
			request.stopPx(),
			request.parties() == null ? 0 : request.parties().size(),
			request.additionalFields() == null ? 0 : request.additionalFields().size());

		SendMessageService.NewOrderSubmissionResult result = sendMessageService.sendNewOrder(targetCompId.trim(), request);
		log.info("Completed /api/send-message request: targetCompId={}, sentNow={}, sent={}, sessionId={}",
			result.targetCompId(),
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
