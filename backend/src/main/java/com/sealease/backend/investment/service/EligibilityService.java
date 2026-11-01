package com.sealease.backend.investment.service;

import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.referral.service.ReferralSchemeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who may invest (Rules 1–2): an active account with approved KYC and a verified bank account (for payouts and
 * withdrawals), in any open plan for their investor type.
 */
@Service
public class EligibilityService {

	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final BankAccountService bankAccounts;
	private final ReferralSchemeService schemes;

	public EligibilityService(UserAccountService accounts, UserProfileService profiles, BankAccountService bankAccounts,
			ReferralSchemeService schemes) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.bankAccounts = bankAccounts;
		this.schemes = schemes;
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
		if (!bankAccounts.hasVerifiedAccount(userId)) {
			reasons.add("Add a bank account and have it verified before investing");
		}
		if (schemes.schemeOf(userId) != product.getAudience()) {
			reasons.add("This plan is not offered to your investor type");
		}
		if (!product.isAcceptingInvestments()) {
			reasons.add("This plan is not accepting investments");
		}
		return InvestorEligibility.of(reasons);
	}

}
