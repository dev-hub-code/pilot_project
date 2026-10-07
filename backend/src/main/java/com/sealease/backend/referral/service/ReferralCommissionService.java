package com.sealease.backend.referral.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.earning.event.PayoutPaidEvent;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pays referral commissions when a monthly payout is credited, in the payout's transaction: each
 * month of the tenure, a referred investor's investment in the container earns their uplines
 * (levels 1-4) the rates in force, which admins set - e.g. at a 2% level-1 rate, ₹1,00,000 earns ₹2,000 a month. The platform
 * pays (referral expense); the investor's own earnings are untouched. An upline who is not active
 * or not verified forfeits that level's commission - it is not passed further up.
 */
@Service
public class ReferralCommissionService {

	private static final String ENTITY = "PAYOUT";

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

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onPayoutPaid(PayoutPaidEvent event) {
		if (!event.invested().isPositive()) {
			return;
		}
		Instant now = clock.instant();
		ReferralRateVersion version = rates.inForceAt(now);
		List<ReferralEarning> commissions = new ArrayList<>();
		int forfeited = 0;
		for (Upline upline : referrals.uplines(event.userId())) {
			Money amount = version.commission(upline.level(), event.invested());
			if (!amount.isPositive()) {
				continue;
			}
			if (!referrals.ineligibility(upline.userId()).isEmpty()) {
				forfeited++;
				continue;
			}
			commissions.add(new ReferralEarning(event.installmentId(), event.userId(), upline.userId(), event.productId(),
					event.installmentNumber(), upline.level(), event.invested(), version.percentFor(upline.level()), amount,
					version.getId(), now));
		}
		if (commissions.isEmpty()) {
			return;
		}

		Money total = commissions.stream().map(ReferralEarning::amount).reduce(Money::plus).orElseThrow();
		Map<UUID, Money> perBeneficiary = new LinkedHashMap<>();
		commissions.forEach(c -> perBeneficiary.merge(c.getBeneficiaryUserId(), c.amount(), Money::plus));
		List<Posting> postings = new ArrayList<>();
		postings.add(Posting.debit(AccountType.PLATFORM_REFERRAL_EXPENSE, null, total));
		perBeneficiary.forEach((userId, amount) -> postings.add(Posting.credit(AccountType.INVESTOR_EARNINGS, userId, amount)));
		UUID transactionId = ledger.post(TransactionType.REFERRAL_COMMISSION, event.installmentId().toString(),
				"Referral commissions on %s payout %d".formatted(event.productCode(), event.installmentNumber()),
				null, postings);

		List<ReferralEarning> saved = earnings.saveAll(commissions);
		audit.record(AuditRecord.of(null, AuditAction.REFERRAL_COMMISSIONS_PAID, ENTITY, event.installmentId())
			.withNewValue(Map.of("total", total.toString(), "commissions", saved.size(),
					"beneficiaries", perBeneficiary.size(), "forfeited", forfeited,
					"rateVersionId", version.getId().toString(), "ledgerTransactionId", transactionId.toString())));
		for (ReferralEarning c : saved) {
			outbox.publish(DomainEvent.of(KafkaTopics.REFERRAL_EARNING_CREATED, "ReferralEarningCreated",
					"REFERRAL_EARNING", c.getId(),
					Map.of("referralEarningId", c.getId().toString(), "beneficiaryUserId", c.getBeneficiaryUserId().toString(),
							"sourceUserId", c.getSourceUserId().toString(), "level", c.getLevel(),
							"installmentId", c.getSourceInstallmentId().toString(), "amount", c.amount().amount().toPlainString(),
							"currency", c.amount().currency().getCurrencyCode())));
		}
	}

}
