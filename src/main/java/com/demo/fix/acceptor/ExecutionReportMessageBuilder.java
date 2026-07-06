package com.demo.fix.acceptor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import quickfix.Group;
import quickfix.Message;
import quickfix.fix44.ExecutionReport;

@Component
public class ExecutionReportMessageBuilder {

	private static final Logger log = LoggerFactory.getLogger(ExecutionReportMessageBuilder.class);

	public Message build(ExecutionReportRequest request) {
		log.info("Building FIX ExecutionReport: clOrdId={}, execId={}, execType={}, ordStatus={}, symbol={}, side={}, orderQty={}",
			request.clOrdId(), request.execId(), request.execType(), request.ordStatus(), request.symbol(), request.side(), request.orderQty());

		ExecutionReport message = new ExecutionReport();

		set(message, 144, request.field144());
		set(message, 6, request.avgPx());
		set(message, 11, request.clOrdId());
		set(message, 14, request.cumQty());
		set(message, 15, request.currency());
		set(message, 17, request.execId());
		set(message, 30, request.lastMkt());
		set(message, 31, request.lastPx());
		set(message, 32, request.lastQty());
		set(message, 37, request.orderId());
		set(message, 38, request.orderQty());
		set(message, 39, request.ordStatus());
		set(message, 40, request.ordType());
		set(message, 41, request.origClOrdId());
		set(message, 54, request.side());
		set(message, 55, request.symbol());
		set(message, 58, request.text());
		set(message, 60, request.transactTime());
		set(message, 64, request.settleDate());
		set(message, 75, request.tradeDate());
		set(message, 150, request.execType());
		set(message, 151, request.leavesQty());
		set(message, 167, request.securityType());
		set(message, 48, request.securityID());
		set(message, 22, request.securityIDSource());
		set(message, 207, request.securityExchange());
		set(message, 1, request.account());
		set(message, 19, request.execRefId());
		set(message, 20, request.execTransType());
		set(message, 21, request.handlInst());
		set(message, 29, request.lastCapacity());
		set(message, 44, request.price());
		set(message, 59, request.timeInForce());
		set(message, 63, request.settlType());
		set(message, 76, request.execBroker());
		set(message, 77, request.positionEffect());
		set(message, 118, request.netMoney());
		set(message, 119, request.settlCurrAmt());
		set(message, 120, request.settlCurrency());
		set(message, 155, request.settlCurrFxRate());
		set(message, 156, request.settlCurrFxRateCalc());
		set(message, 218, request.benchmarkPrice());
		set(message, 354, request.encodedTextLen());
		set(message, 355, request.encodedText());
		set(message, 356, request.encodedSubjectLen());
		set(message, 357, request.encodedSubject());
		set(message, 378, request.execRestatementReason());
		set(message, 381, request.grossTradeAmt());
		set(message, 423, request.dayOrderQty());
		set(message, 424, request.dayCumQty());
		set(message, 425, request.dayAvgPx());
		set(message, 432, request.expireDate());
		set(message, 528, request.orderCapacity());
		set(message, 541, request.maturityDate());
		set(message, 647, request.benchmarkPriceType());
		set(message, 662, request.benchmarkSecurityID());
		set(message, 663, request.benchmarkSecurityIDSource());
		set(message, 667, request.contractMultiplier());
		set(message, 721, request.settlDate());
		set(message, 797, request.tradeLiquidityIndicator());
		set(message, 854, request.qtyType());

		addParties(message, request.parties());
		applyAdditionalFields(message, request.additionalFields());

		log.info("Built FIX ExecutionReport with {} additional field(s) and {} party block(s)",
			request.additionalFields().size(),
			request.parties().size());
		return message;
	}

	private void addParties(Message message, List<ExecutionReportRequest.Party> parties) {
		if (parties == null || parties.isEmpty()) {
			return;
		}

		for (ExecutionReportRequest.Party party : parties) {
			Group partyGroup = new Group(453, 448, new int[] {448, 447, 452, 802});
			set(partyGroup, 448, party.partyId());
			set(partyGroup, 447, party.partyIdSource());
			set(partyGroup, 452, party.partyRole());

			if (party.subIds() != null && !party.subIds().isEmpty()) {
				for (ExecutionReportRequest.PartySubId subId : party.subIds()) {
					Group subGroup = new Group(802, 523, new int[] {523, 803});
					set(subGroup, 523, subId.subId());
					set(subGroup, 803, subId.subIdType());
					partyGroup.addGroup(subGroup);
				}
			}

			message.addGroup(partyGroup);
		}
	}

	private void applyAdditionalFields(ExecutionReport message, List<ExecutionReportRequest.AdditionalField> additionalFields) {
		if (additionalFields == null || additionalFields.isEmpty()) {
			return;
		}

		for (ExecutionReportRequest.AdditionalField entry : additionalFields) {
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

	private void set(Message message, int tag, String value) {
		if (value != null && !value.isBlank()) {
			message.setString(tag, value);
		}
	}

	private void set(Group group, int tag, String value) {
		if (value != null && !value.isBlank()) {
			group.setString(tag, value);
		}
	}
}
