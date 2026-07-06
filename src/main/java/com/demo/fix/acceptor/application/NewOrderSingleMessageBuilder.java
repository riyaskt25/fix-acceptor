package com.demo.fix.acceptor.application;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.demo.fix.acceptor.api.NewOrderRequest;

import quickfix.Group;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;
import quickfix.fix44.NewOrderSingle;

@Component
public class NewOrderSingleMessageBuilder {

	private static final Logger log = LoggerFactory.getLogger(NewOrderSingleMessageBuilder.class);

	public NewOrderSingle build(NewOrderRequest request) {
		log.info("Building FIX NewOrderSingle: clOrdId={}, symbol={}, side={}, ordType={}, orderQty={}, price={}, stopPx={}",
			request.clOrdId(), request.symbol(), request.side(), request.ordType(), request.orderQty(), request.price(), request.stopPx());

		NewOrderSingle message = new NewOrderSingle(
			new ClOrdID(require(request.clOrdId(), "clOrdId")),
			new Side(parseSide(request.side())),
			new TransactTime(parseTransactTime(request.transactTime())),
			new OrdType(parseOrdType(request.ordType())));

		message.set(new Symbol(require(request.symbol(), "symbol")));
		message.set(new OrderQty(requireDouble(request.orderQty(), "orderQty")));
		setIfPresent(message, 44, request.price());
		setIfPresent(message, 99, request.stopPx());
		setIfPresent(message, 21, request.handlInst());
		setIfPresent(message, 59, request.timeInForce());
		setIfPresent(message, 1, request.account());
		setIfPresent(message, 15, request.currency());
		setIfPresent(message, 58, request.text());
		setIfPresent(message, 48, request.securityID());
		setIfPresent(message, 22, request.securityIDSource());
		setIfPresent(message, 207, request.securityExchange());
		setIfPresent(message, 100, request.exDestination());
		setIfPresent(message, 528, request.orderCapacity());
		setIfPresent(message, 111, request.maxFloor());
		setIfPresent(message, 110, request.minQty());
		setIfPresent(message, 152, request.cashOrderQty());
		setIfPresent(message, 63, request.settlType());
		setIfPresent(message, 120, request.settlCurrency());
		setIfPresent(message, 77, request.openClose());
		setIfPresent(message, 376, request.complianceID());
		setIfPresent(message, 204, request.customerOrFirm());
		setIfPresent(message, 432, request.expireDate());

		addParties(message, request.parties());
		applyAdditionalFields(message, request.additionalFields());

		log.info("Built FIX NewOrderSingle with {} additional field(s) and {} party block(s)",
			request.additionalFields().size(),
			request.parties().size());
		return message;
	}

	private void addParties(NewOrderSingle message, List<NewOrderRequest.Party> parties) {
		if (parties == null || parties.isEmpty()) {
			return;
		}

		for (NewOrderRequest.Party party : parties) {
			Group partyGroup = new Group(453, 448, new int[] {448, 447, 452, 802});
			setIfPresent(partyGroup, 448, party.partyId());
			setIfPresent(partyGroup, 447, party.partyIdSource());
			setIfPresent(partyGroup, 452, party.partyRole());

			if (party.subIds() != null && !party.subIds().isEmpty()) {
				for (NewOrderRequest.PartySubId subId : party.subIds()) {
					Group subGroup = new Group(802, 523, new int[] {523, 803});
					setIfPresent(subGroup, 523, subId.subId());
					setIfPresent(subGroup, 803, subId.subIdType());
					partyGroup.addGroup(subGroup);
				}
			}

			message.addGroup(partyGroup);
		}
	}

	private void applyAdditionalFields(NewOrderSingle message, List<NewOrderRequest.AdditionalField> additionalFields) {
		if (additionalFields == null || additionalFields.isEmpty()) {
			return;
		}

		for (NewOrderRequest.AdditionalField entry : additionalFields) {
			String tagText = entry.tag();
			String value = entry.value();
			if (value == null || value.isBlank()) {
				continue;
			}
			try {
				int tag = Integer.parseInt(tagText);
				message.setString(tag, value);
				log.debug("Applied additional FIX field: tag={}, value={}", tag, value);
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("additionalFields keys must be numeric FIX tags: " + tagText, exception);
			}
		}
	}

	private char parseSide(String side) {
		if (side == null || side.isBlank()) {
			return Side.BUY;
		}
		String normalized = side.trim().toUpperCase();
		return switch (normalized) {
			case "1", "BUY" -> Side.BUY;
			case "2", "SELL" -> Side.SELL;
			default -> Side.BUY;
		};
	}

	private char parseOrdType(String ordType) {
		if (ordType == null || ordType.isBlank()) {
			return '1';
		}
		String normalized = ordType.trim().toUpperCase();
		return switch (normalized) {
			case "1", "MARKET" -> '1';
			case "2", "LIMIT" -> '2';
			case "3", "STOP" -> '3';
			case "4", "STOP_LIMIT" -> '4';
			default -> '1';
		};
	}

	private LocalDateTime parseTransactTime(String transactTime) {
		if (transactTime == null || transactTime.isBlank()) {
			return LocalDateTime.now();
		}
		String normalized = transactTime.trim().replace('T', ' ');
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SSSSSSSSS]");
		return LocalDateTime.parse(normalized, formatter);
	}

	private String require(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return value;
	}

	private double requireDouble(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(fieldName + " must be numeric", exception);
		}
	}

	private void setIfPresent(NewOrderSingle message, int tag, String value) {
		if (value != null && !value.isBlank()) {
			message.setString(tag, value);
		}
	}

	private void setIfPresent(Group group, int tag, String value) {
		if (value != null && !value.isBlank()) {
			group.setString(tag, value);
		}
	}
}
