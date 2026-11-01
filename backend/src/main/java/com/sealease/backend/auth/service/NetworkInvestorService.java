package com.sealease.backend.auth.service;

import com.sealease.backend.auth.config.AuthProperties;
import com.sealease.backend.auth.dto.CreateInvestorRequest;
import com.sealease.backend.common.validation.PhoneNumbers;
import com.sealease.backend.referral.service.ReferralSchemeService;
import com.sealease.backend.referral.service.ReferralService;
import com.sealease.backend.role.dto.StaffCredentialsResponse;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserAccountService.IssuedCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Investor accounts made by administrators: they are on the network referral scheme, unlike investors who register
 * themselves. Credentials are a temporary password, shown once, that must be replaced at first sign-in.
 */
@Service
public class NetworkInvestorService {

	private final UserAccountService accounts;
	private final UserRoleService userRoles;
	private final ReferralService referrals;
	private final ReferralSchemeService schemes;
	private final AuthProperties properties;

	public NetworkInvestorService(UserAccountService accounts, UserRoleService userRoles, ReferralService referrals,
			ReferralSchemeService schemes, AuthProperties properties) {
		this.accounts = accounts;
		this.userRoles = userRoles;
		this.referrals = referrals;
		this.schemes = schemes;
		this.properties = properties;
	}

	@Transactional
	public StaffCredentialsResponse create(UUID actorId, CreateInvestorRequest request) {
		IssuedCredentials issued = accounts.createInvestorAccount(request.email(), request.firstName(), request.lastName(),
				PhoneNumbers.require(request.phone(), request.phoneCountry()), actorId);
		UUID userId = issued.account().id();
		userRoles.grantSystemRole(userId, properties.defaultRole());
		// Enrolled first: their own setting stands, whatever the referrer's is.
		schemes.enrol(actorId, userId, request.signupsJoinNetwork());
		if (request.referralCode() != null && !request.referralCode().isBlank()) {
			referrals.link(userId, request.referralCode());
		}
		return new StaffCredentialsResponse(userId, issued.account().email(), issued.account().firstName(),
				issued.account().lastName(), Set.of(properties.defaultRole()), issued.temporaryPassword(),
				issued.expiresAt(), false);
	}

}
