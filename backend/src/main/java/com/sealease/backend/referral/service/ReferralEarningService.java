package com.sealease.backend.referral.service;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.Lease;
import com.sealease.backend.investment.service.LeaseService;
import com.sealease.backend.referral.dto.ReferralEarningResponse;
import com.sealease.backend.referral.entity.ReferralEarning;
import com.sealease.backend.referral.repository.ReferralEarningRepository;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Referral commission history, for the earning investor and for staff. */
@Service
public class ReferralEarningService {

	private final ReferralEarningRepository earnings;
	private final LeaseService leases;
	private final UserAccountService accounts;

	public ReferralEarningService(ReferralEarningRepository earnings, LeaseService leases, UserAccountService accounts) {
		this.earnings = earnings;
		this.leases = leases;
		this.accounts = accounts;
	}

	/** The investor's own commissions; the referred investors appear by display name only. */
	@Transactional(readOnly = true)
	public Page<ReferralEarningResponse> mine(UUID userId, Pageable pageable) {
		Page<ReferralEarning> page = earnings.findByBeneficiaryUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
		Map<UUID, UserAccount> sources = accounts.getAccounts(page.map(ReferralEarning::getSourceUserId).toSet());
		Map<UUID, Lease> products = leases.leases(page.map(ReferralEarning::getProductId).toSet());
		return page.map(e -> view(e, ReferralService.displayName(sources.get(e.getSourceUserId())), products, false));
	}

	@Transactional(readOnly = true)
	public Page<ReferralEarningResponse> search(UUID beneficiaryId, UUID sourceId, Pageable pageable) {
		Page<ReferralEarning> page = earnings.findAll(matching(beneficiaryId, sourceId), pageable);
		Map<UUID, UserAccount> sources = accounts.getAccounts(page.map(ReferralEarning::getSourceUserId).toSet());
		Map<UUID, Lease> products = leases.leases(page.map(ReferralEarning::getProductId).toSet());
		return page.map(e -> {
			UserAccount source = sources.get(e.getSourceUserId());
			return view(e, source == null ? null : source.firstName() + " " + source.lastName(), products, true);
		});
	}

	private static ReferralEarningResponse view(ReferralEarning e, String sourceName, Map<UUID, Lease> products,
			boolean staff) {
		Lease lease = products.get(e.getProductId());
		return new ReferralEarningResponse(e.getId(), e.getLevel(), sourceName, staff ? e.getSourceUserId() : null,
				staff ? e.getBeneficiaryUserId() : null, lease == null ? null : lease.code(), e.getPeriodNumber(),
				MoneyResponse.from(e.base()), e.getRatePercent(), MoneyResponse.from(e.amount()), e.getCreatedAt());
	}

	private static Specification<ReferralEarning> matching(UUID beneficiaryId, UUID sourceId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (beneficiaryId != null) {
				predicates.add(cb.equal(root.get("beneficiaryUserId"), beneficiaryId));
			}
			if (sourceId != null) {
				predicates.add(cb.equal(root.get("sourceUserId"), sourceId));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
