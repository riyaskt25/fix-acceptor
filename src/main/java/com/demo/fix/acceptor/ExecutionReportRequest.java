package com.demo.fix.acceptor;

import java.util.ArrayList;
import java.util.List;

public record ExecutionReportRequest(
	String field144,
	String clOrdId,
	String origClOrdId,
	String orderId,
	String execId,
	String execType,
	String ordStatus,
	String ordType,
	String side,
	String symbol,
	String securityType,
	String securityID,
	String securityIDSource,
	String securityExchange,
	String account,
	String currency,
	String text,
	String transactTime,
	String tradeDate,
	String settleDate,
	String maturityDate,
	String execTransType,
	String execRefId,
	String execRestatementReason,
	String handlInst,
	String timeInForce,
	String lastMkt,
	String orderQty,
	String cumQty,
	String leavesQty,
	String avgPx,
	String lastQty,
	String lastPx,
	String price,
	String lastCapacity,
	String orderCapacity,
	String cashOrderQty,
	String settlType,
	String settlCurrency,
	String settlDate,
	String settlCurrAmt,
	String settlCurrFxRate,
	String settlCurrFxRateCalc,
	String grossTradeAmt,
	String netMoney,
	String benchmarkPrice,
	String benchmarkPriceType,
	String benchmarkSecurityID,
	String benchmarkSecurityIDSource,
	String encodedText,
	String encodedSubject,
	String encodedTextLen,
	String encodedSubjectLen,
	String execBroker,
	String positionEffect,
	String contractMultiplier,
	String qtyType,
	String dayOrderQty,
	String dayCumQty,
	String dayAvgPx,
	String expireDate,
	String tradeLiquidityIndicator,
	List<Party> parties,
	List<AdditionalField> additionalFields
) {
	public ExecutionReportRequest {
		parties = parties == null ? new ArrayList<>() : List.copyOf(parties);
		additionalFields = additionalFields == null ? new ArrayList<>() : List.copyOf(additionalFields);
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

	public record PartySubId(
		String subId,
		String subIdType
	) {
	}

	public record AdditionalField(
		String tag,
		String value
	) {
	}
}
