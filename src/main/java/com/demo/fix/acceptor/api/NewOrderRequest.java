package com.demo.fix.acceptor.api;

import java.util.ArrayList;
import java.util.List;

public record NewOrderRequest(
	String clOrdId,
	String symbol,
	String side,
	String transactTime,
	String ordType,
	String orderQty,
	String price,
	String stopPx,
	String handlInst,
	String timeInForce,
	String account,
	String currency,
	String text,
	String securityID,
	String securityIDSource,
	String securityExchange,
	String exDestination,
	String orderCapacity,
	String maxFloor,
	String minQty,
	String cashOrderQty,
	String settlType,
	String settlCurrency,
	String openClose,
	String positionEffect,
	String complianceID,
	String rule80A,
	String customerOrFirm,
	String expireDate,
	List<AdditionalField> additionalFields,
	List<Party> parties
) {
	public NewOrderRequest {
		additionalFields = additionalFields == null ? new ArrayList<>() : List.copyOf(additionalFields);
		parties = parties == null ? new ArrayList<>() : List.copyOf(parties);
	}

	public record AdditionalField(String tag, String value) {
	}

	public record Party(
		String partyId,
		String partyIdSource,
		String partyRole,
		List<PartySubId> subIds
	) {
		public Party {
			subIds = subIds == null ? new ArrayList<>() : List.copyOf(subIds);
		}
	}

	public record PartySubId(String subId, String subIdType) {
	}
}
