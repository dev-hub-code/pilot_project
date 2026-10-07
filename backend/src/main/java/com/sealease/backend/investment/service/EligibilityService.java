package com.sealease.backend.investment.service;

import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.InvestorType;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who may invest in what (Rules 1–2): KYC approved, active account, HNI-only standalone containers. */
@Service
public class EligibilityService {

	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final Clock clock;

	public EligibilityService(UserAccountService accounts, UserProfileService profiles, Clock clock) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public InvestorEligibility evaluate(UUID userId, InvestmentProduct product) {
		List<String> reasons = new ArrayList<>();
		UserAccount account = accounts.getAccount(userId);
		if (account.status() != UserStatus.ACTIVE) {
			reasons.add("Your account is not active");
		}
		if (profiles.kycStatusOf(userId) != KycStatus.APPROVED) {
			reasons.add("Verify your identity before investing");
		}
		InvestorType investorType = profiles.investorTypeOf(userId);
		if (!product.getInvestmentType().isOpenTo(investorType)) {
			reasons.add("Standalone containers are reserved for HNI investors");
		}
		if (!product.isAcceptingInvestmentsAt(clock.instant())) {
			reasons.add("This offering is not accepting investments");
		}
		else if (product.available().isZero()) {
			reasons.add("This offering is fully subscribed");
		}
		return InvestorEligibility.of(reasons);
	}

}
