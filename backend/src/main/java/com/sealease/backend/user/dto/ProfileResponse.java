package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.entity.UserStatus;

import java.time.LocalDate;
import java.util.UUID;

/** The caller's own profile. The tax id is only ever returned masked. */
public record ProfileResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		UserStatus status,
		String phone,
		LocalDate dateOfBirth,
		String nationality,
		Address address,
		String taxResidencyCountry,
		String taxIdMasked,
		KycStatus kycStatus,
		boolean emailNotifications,
		boolean smsNotifications,
		boolean twoFactorEnabled) {

	public record Address(String line1, String line2, String city, String stateRegion, String postalCode,
			String country) {
	}

	public static ProfileResponse from(UserProfile p) {
		var u = p.getUser();
		return new ProfileResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus(),
				p.getPhone(), p.getDateOfBirth(), p.getNationality(),
				new Address(p.getAddressLine1(), p.getAddressLine2(), p.getCity(), p.getStateRegion(),
						p.getPostalCode(), p.getCountry()),
				p.getTaxResidencyCountry(), mask(p.getTaxIdLast4()), p.getKycStatus(),
				p.isEmailNotifications(), p.isSmsNotifications(), u.isMfaEnabled());
	}

	static String mask(String last4) {
		return last4 == null ? null : "•••• " + last4;
	}

}
