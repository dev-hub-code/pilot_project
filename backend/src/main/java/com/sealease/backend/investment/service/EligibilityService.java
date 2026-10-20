package com.sealease.backend.investment.service;

import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who may invest (Rules 1–2): an active account with approved KYC and a verified bank account (for payouts and
 * withdrawals), in any open plan.
 */
@Service
public class EligibilityService {

	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final BankAccountService bankAccounts;

	public EligibilityService(UserAccountService accounts, UserProfileService profiles, BankAccountService bankAccounts) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.bankAccounts = bankAccounts;
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
		if (!product.isAcceptingInvestments()) {
			reasons.add("This container is not accepting investments");
		}
		return InvestorEligibility.of(reasons);
	}

}
