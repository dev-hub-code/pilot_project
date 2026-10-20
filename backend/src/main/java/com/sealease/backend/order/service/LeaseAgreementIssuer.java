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
 * Issues a holding's lease agreement once it has a container, from the holding's plan, container and lease. Called in
 * the transaction that assigns the container. A holding whose lease has not started (it awaits the investor's
 * acceptance) gets an agreement to accept; one already on lease gets one stating its dates.
 */
@Component
class LeaseAgreementIssuer {

	private final HoldingService holdings;
	private final LeaseAgreementService agreements;

	LeaseAgreementIssuer(HoldingService holdings, LeaseAgreementService agreements) {
		this.holdings = holdings;
		this.agreements = agreements;
	}

	/** @param actorId who assigned the container; {@code null} when it was leased on payment */
	@Transactional(propagation = Propagation.MANDATORY)
	LeaseAgreementResponse issue(UUID actorId, UUID holdingId, UUID userId, String orderNumber) {
		return issue(actorId, holdings.get(holdingId), userId, orderNumber);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	LeaseAgreementResponse issue(UUID actorId, HoldingResponse h, UUID userId, String orderNumber) {
		if (h.container() == null) {
			throw new IllegalStateException("Holding " + h.id() + " has no container yet");
		}
		return agreements.issue(actorId, new AgreementFacts(h.id(), h.orderId(), orderNumber, userId, h.productCode(),
				h.productTitle(), h.container().containerNumber(), h.container().containerType().label(),
				h.container().currentLocation(), money(h.amount()), h.monthlyRentPercent(), money(h.monthlyRent()),
				h.tenureMonths(), h.leaseStartsOn(), h.leaseEndsOn()));
	}

	private static Money money(MoneyResponse m) {
		return Money.of(new BigDecimal(m.amount()), java.util.Currency.getInstance(m.currency()));
	}

}
