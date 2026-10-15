package com.sealease.backend.referral.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.earning.event.RentCreditedEvent;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.referral.dto.Upline;
import com.sealease.backend.referral.entity.ReferralEarning;
import com.sealease.backend.referral.entity.ReferralRateVersion;
import com.sealease.backend.referral.repository.ReferralEarningRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pays referral commissions as a referred investor's rent is credited, in that transaction: for each
 * month of the lease (credited hour by hour), their investment in the container earns their uplines
 * (levels 1-4) the rates in force, which admins set - e.g. at a 2% level-1 rate, ₹1,00,000 earns ₹2,000 a month. The platform
 * pays (referral expense); the investor's own earnings are untouched. An upline who is not active
 * or not verified forfeits that level's commission - it is not passed further up.
 */
@Service
public class ReferralCommissionService {

	private static final String ENTITY = "RENT_CREDIT";

	private final ReferralService referrals;
	private final ReferralRateService rates;
	private final ReferralEarningRepository earnings;
	private final LedgerService ledger;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final Clock clock;

	public ReferralCommissionService(ReferralService referrals, ReferralRateService rates,
			ReferralEarningRepository earnings, LedgerService ledger, OutboxPublisher outbox, AuditService audit,
			Clock clock) {
		this.referrals = referrals;
		this.rates = rates;
		this.earnings = earnings;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Pays the uplines commission on the hours of rent just credited. Commission is worked out on the lease months
	 * elapsed after these hours, less what it was before them, so over a whole lease it adds up to exactly the monthly
	 * commission × tenure (no rounding drift from paying it hour by hour).
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onRentCredited(RentCreditedEvent event) {
		Money baseBefore = base(event.invested(), event.monthsBefore());
		Money baseAfter = base(event.invested(), event.monthsAfter());
		Money base = baseAfter.minus(baseBefore);
		if (!base.isPositive()) {
			return;
		}
		Instant now = clock.instant();
		ReferralRateVersion version = rates.inForceAt(now);
		List<ReferralEarning> commissions = new ArrayList<>();
		int forfeited = 0;
		for (Upline upline : referrals.uplines(event.userId())) {
			Money amount = version.commission(upline.level(), baseAfter).minus(version.commission(upline.level(), baseBefore));
			if (!amount.isPositive()) {
				continue;
			}
			if (!referrals.ineligibility(upline.userId()).isEmpty()) {
				forfeited++;
				continue;
			}
			commissions.add(new ReferralEarning(event.holdingId(), event.creditId(), event.userId(), upline.userId(),
					event.productId(), upline.level(), base, version.percentFor(upline.level()), amount, version.getId(), now));
		}
		if (commissions.isEmpty()) {
			return;
		}

		Money total = commissions.stream().map(ReferralEarning::amount).reduce(Money::plus).orElseThrow();
		Map<UUID, Money> perBeneficiary = new LinkedHashMap<>();
		commissions.forEach(c -> perBeneficiary.merge(c.getBeneficiaryUserId(), c.amount(), Money::plus));
		List<Posting> postings = new ArrayList<>();
		postings.add(Posting.debit(AccountType.PLATFORM_REFERRAL_EXPENSE, null, total));
		// Commission has its own balance: paid by bank or in cash, as the investor chose for the year.
		perBeneficiary.forEach((userId, amount) -> postings.add(Posting.credit(AccountType.INVESTOR_REFERRAL_EARNINGS, userId, amount)));
		UUID transactionId = ledger.post(TransactionType.REFERRAL_COMMISSION, event.creditId().toString(),
				"Referral commissions on %s rent, %s to %s".formatted(event.productCode(), event.from(), event.to()),
				null, postings);
		earnings.saveAll(commissions);
		audit.record(AuditRecord.of(null, AuditAction.REFERRAL_COMMISSIONS_PAID, ENTITY, event.creditId())
			.withNewValue(Map.of("total", total.toString(), "commissions", commissions.size(),
					"beneficiaries", perBeneficiary.size(), "forfeited", forfeited,
					"holdingId", event.holdingId().toString(),
					"rateVersionId", version.getId().toString(), "ledgerTransactionId", transactionId.toString())));
		// One event per credit, not per commission: commissions arrive every hour.
		outbox.publish(DomainEvent.of(KafkaTopics.REFERRAL_EARNING_CREATED, "ReferralCommissionsCredited",
				ENTITY, event.creditId(),
				Map.of("creditId", event.creditId().toString(), "holdingId", event.holdingId().toString(),
						"sourceUserId", event.userId().toString(), "total", total.amount().toPlainString(),
						"currency", total.currency().getCurrencyCode(), "commissions", commissions.size())));
	}

	/** The commission base for {@code months} of the lease: invested × months, rounded down to the minor unit. */
	static Money base(Money invested, BigDecimal months) {
		if (months.signum() <= 0) {
			return Money.zero(invested.currency());
		}
		int digits = Math.max(invested.currency().getDefaultFractionDigits(), 0);
		return Money.of(invested.amount().multiply(months).setScale(digits, RoundingMode.DOWN), invested.currency());
	}

}
