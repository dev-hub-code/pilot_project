package com.sealease.backend.referral.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.service.OfferingQuery;
import com.sealease.backend.referral.dto.ReferralEarningResponse;
import com.sealease.backend.referral.dto.ReferralMonth;
import com.sealease.backend.referral.repository.MonthlyLevelTotal;
import com.sealease.backend.referral.repository.ReferralEarningGroup;
import com.sealease.backend.referral.repository.ReferralEarningRepository;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Referral commission totals for the earning investor, and the commission history for staff. */
@Service
public class ReferralEarningService {

	private final ReferralEarningRepository earnings;
	private final OfferingQuery plans;
	private final UserAccountService accounts;

	public ReferralEarningService(ReferralEarningRepository earnings, OfferingQuery plans, UserAccountService accounts) {
		this.earnings = earnings;
		this.plans = plans;
		this.accounts = accounts;
	}

	/** Commission the investor has earned over all time, per currency. */
	@Transactional(readOnly = true)
	public List<MoneyResponse> totalEarned(UUID userId) {
		return ReferralService.totals(earnings.totalsBySource(userId));
	}

	/** The investor's commission per calendar month (UTC) and level, latest month first. */
	@Transactional(readOnly = true)
	public List<ReferralMonth> monthly(UUID userId) {
		Map<String, Money[]> byMonth = new LinkedHashMap<>();
		for (MonthlyLevelTotal row : earnings.monthlyByLevel(userId)) {
			Currency currency = Currency.getInstance(row.getCurrency());
			Money[] levels = byMonth.computeIfAbsent(row.getMonth() + "|" + row.getCurrency(), k -> {
				Money[] zeros = new Money[ReferralService.LEVELS];
				Arrays.fill(zeros, Money.zero(currency));
				return zeros;
			});
			levels[row.getLevel().intValue() - 1] = Money.of(row.getAmount(), currency);
		}
		List<ReferralMonth> months = new ArrayList<>();
		byMonth.forEach((key, levels) -> months.add(new ReferralMonth(key.substring(0, key.indexOf('|')),
				Arrays.stream(levels).map(MoneyResponse::from).toList(),
				MoneyResponse.from(Arrays.stream(levels).reduce(Money::plus).orElseThrow()))));
		return months;
	}

	@Transactional(readOnly = true)
	public Page<ReferralEarningResponse> search(UUID beneficiaryId, UUID sourceId, Pageable pageable) {
		Page<ReferralEarningGroup> page = earnings.grouped(beneficiaryId, sourceId, unsorted(pageable));
		Map<UUID, UserAccount> sources = accounts.getAccounts(page.map(ReferralEarningGroup::getSourceUserId).toSet());
		Map<UUID, OfferingTerms> products = plans.terms(page.map(ReferralEarningGroup::getProductId).toSet());
		return page.map(e -> {
			UserAccount source = sources.get(e.getSourceUserId());
			return view(e, source == null ? null : source.firstName() + " " + source.lastName(), products, true);
		});
	}

	private static ReferralEarningResponse view(ReferralEarningGroup e, String sourceName,
			Map<UUID, OfferingTerms> products, boolean staff) {
		OfferingTerms plan = products.get(e.getProductId());
		Currency currency = Currency.getInstance(e.getCurrency());
		// Stable id for the group: the holding, month, level and beneficiary it adds up.
		UUID id = UUID.nameUUIDFromBytes((e.getHoldingId() + ":" + e.getMonth() + ":" + e.getLevel() + ":"
				+ e.getBeneficiaryUserId() + ":" + e.getCurrency())
			.getBytes(StandardCharsets.UTF_8));
		return new ReferralEarningResponse(id, e.getLevel().intValue(), sourceName, staff ? e.getSourceUserId() : null,
				staff ? e.getBeneficiaryUserId() : null, plan == null ? null : plan.code(), e.getMonth(),
				MoneyResponse.from(Money.of(e.getBaseAmount(), currency)), e.getRatePercent(),
				MoneyResponse.from(Money.of(e.getAmount(), currency)), e.getLastCreditedAt());
	}

	/** The grouped history has its own order (latest first); page and size are kept. */
	private static Pageable unsorted(Pageable pageable) {
		return pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : pageable;
	}

}
