package com.sealease.backend.order.service;

import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.service.AgreementFacts;
import com.sealease.backend.agreement.service.AgreementFactsSource;
import com.sealease.backend.agreement.service.LeaseAgreementService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.order.entity.InvestmentOrder;
import com.sealease.backend.order.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Issues a holding's investment agreement from its plan and terms, in the transaction that confirms the order. The
 * investor accepts it to start the term. Also gives the agreement module a holding's facts again, so an agreement
 * still waiting for acceptance can be shown in the wording now in force.
 */
@Component
class LeaseAgreementIssuer implements AgreementFactsSource {

	private final HoldingService holdings;
	private final LeaseAgreementService agreements;
	private final OrderRepository orders;

	LeaseAgreementIssuer(HoldingService holdings, LeaseAgreementService agreements, OrderRepository orders) {
		this.holdings = holdings;
		this.agreements = agreements;
		this.orders = orders;
	}

	@Override
	public AgreementFacts pendingFacts(UUID holdingId, UUID userId) {
		HoldingResponse h = holdings.get(holdingId);
		String orderNumber = orders.findById(h.orderId()).map(InvestmentOrder::getOrderNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Order", h.orderId()));
		return facts(h, userId, orderNumber, false);
	}

	/** @param actorId who issued it; {@code null} when issued on payment */
	@Transactional(propagation = Propagation.MANDATORY)
	LeaseAgreementResponse issue(UUID actorId, UUID holdingId, UUID userId, String orderNumber) {
		return issue(actorId, holdings.get(holdingId), userId, orderNumber);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	LeaseAgreementResponse issue(UUID actorId, HoldingResponse h, UUID userId, String orderNumber) {
		return agreements.issue(actorId, facts(h, userId, orderNumber, true));
	}

	/** @param withDates whether to state the lease dates the holding has; without them, the lease awaits acceptance */
	private static AgreementFacts facts(HoldingResponse h, UUID userId, String orderNumber, boolean withDates) {
		return new AgreementFacts(h.id(), h.orderId(), orderNumber, userId, h.productCode(), h.productTitle(),
				money(h.amount()), h.monthlyRentPercent(), money(h.monthlyRent()), h.tenureMonths(),
				withDates ? h.leaseStartsOn() : null, withDates ? h.leaseEndsOn() : null);
	}

	private static Money money(MoneyResponse m) {
		return Money.of(new BigDecimal(m.amount()), java.util.Currency.getInstance(m.currency()));
	}

}
