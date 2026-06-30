package com.demo.fix.acceptor;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@ConditionalOnProperty(prefix = "fix.acceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderSimulationController {

	private final QuickFixAcceptorService acceptorService;

	public OrderSimulationController(QuickFixAcceptorService acceptorService) {
		this.acceptorService = acceptorService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public QuickFixAcceptorService.SubmissionResult submit(@RequestBody OrderSubmissionRequest request) {
		if (request == null || request.targetCompId() == null || request.targetCompId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCompId is required");
		}
		int count = request.count() == null ? 1 : request.count();
		if (count <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be greater than 0");
		}
		if (request.quantity() != null && request.quantity() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than 0");
		}
		return acceptorService.submitOrders(
			request.targetCompId().trim(),
			request.symbol(),
			request.quantity(),
			request.side(),
			count);
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleBadRequest(IllegalArgumentException exception) {
		return exception.getMessage();
	}

	public record OrderSubmissionRequest(
		String targetCompId,
		String symbol,
		Integer quantity,
		String side,
		Integer count
	) {
	}
}
