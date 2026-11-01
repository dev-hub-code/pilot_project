package com.sealease.backend.order.service;

import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.service.AgreementFacts;
import com.sealease.backend.agreement.service.LeaseAgreementService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.service.HoldingService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Issues a holding's investment agreement from its plan and terms, in the transaction that confirms the order. The
 * investor accepts it to start the term.
 */
@Component
class LeaseAgreementIssuer {

	private final HoldingService holdings;
	private final LeaseAgreementService agreements;

	LeaseAgreementIssuer(HoldingService holdings, LeaseAgreementService agreements) {
		this.holdings = holdings;
		this.agreements = agreements;
	}

	/** @param actorId who issued it; {@code null} when issued on payment */
	@Transactional(propagation = Propagation.MANDATORY)
	LeaseAgreementResponse issue(UUID actorId, UUID holdingId, UUID userId, String orderNumber) {
		return issue(actorId, holdings.get(holdingId), userId, orderNumber);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	LeaseAgreementResponse issue(UUID actorId, HoldingResponse h, UUID userId, String orderNumber) {
		return agreements.issue(actorId, new AgreementFacts(h.id(), h.orderId(), orderNumber, userId, h.productCode(),
				h.productTitle(), money(h.amount()), h.monthlyRentPercent(), money(h.monthlyRent()),
				h.tenureMonths(), h.leaseStartsOn(), h.leaseEndsOn()));
	}

	private static Money money(MoneyResponse m) {
		return Money.of(new BigDecimal(m.amount()), java.util.Currency.getInstance(m.currency()));
	}

}
