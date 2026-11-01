package com.sealease.backend.order.service;

import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.service.LeaseAgreementService;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The investor accepts the agreement of a paid investment. Accepting starts the term at the next full hour and, with
 * it, the returns; nothing is earned before. The holding row is locked first, so a double submission starts one term.
 */
@Service
public class AgreementAcceptanceService {

	private final HoldingService holdings;
	private final LeaseAgreementService agreements;
	private final PayoutService payouts;
	private final NotificationService notifications;
	private final Clock clock;

	public AgreementAcceptanceService(HoldingService holdings, LeaseAgreementService agreements, PayoutService payouts,
			NotificationService notifications, Clock clock) {
		this.holdings = holdings;
		this.agreements = agreements;
		this.payouts = payouts;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	public LeaseAgreementResponse accept(UUID userId, UUID holdingId) {
		PayoutTerms terms = holdings.startLease(userId, holdingId, payouts.leaseStartAfter(clock.instant()));
		payouts.schedule(terms);
		HoldingResponse leased = holdings.get(holdingId);
		LeaseAgreementResponse accepted = agreements.accept(userId, holdingId, leased.leaseStartsOn(), leased.leaseEndsOn());
		notifications.notify(userId, "LEASE_STARTED", "Your investment has started",
				terms.productCode() + ": you accepted agreement " + accepted.agreementNumber()
						+ ". Your term starts at the next full hour; returns are credited to your wallet "
						+ payouts.creditInterval().label() + " from then on.", "/portfolio");
		return accepted;
	}

}
