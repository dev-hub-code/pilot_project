package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.entity.UserStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The caller's own profile. The tax id is only ever returned masked. */
public record ProfileResponse(
		UUID id,
		/** The readable ID ("SL03500"). */
		String userCode,
		String email,
		String firstName,
		String lastName,
		UserStatus status,
		String phone,
		LocalDate dateOfBirth,
		String nationality,
		Address address,
		String gstNumber,
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
		return new ProfileResponse(u.getId(), u.getUserCode(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus(),
				p.getPhone(), p.getDateOfBirth(), p.getNationality(),
				new Address(p.getAddressLine1(), p.getAddressLine2(), p.getCity(), p.getStateRegion(),
						p.getPostalCode(), p.getCountry()),
				p.getGstNumber(),
				p.getTaxResidencyCountry(), mask(p.getTaxIdLast4()), p.getKycStatus(),
				p.isEmailNotifications(), p.isSmsNotifications(), u.isMfaEnabled());
	}

	/** The same, with the mobile number cut to its last four digits ("•••••• 3210"). */
	public ProfileResponse withMaskedPhone() {
		String digits = phone == null ? "" : phone.replaceAll("\\D", "");
		String masked = digits.isEmpty() ? phone : "•••••• " + digits.substring(Math.max(0, digits.length() - 4));
		return new ProfileResponse(id, userCode, email, firstName, lastName, status, masked, dateOfBirth, nationality, address,
				gstNumber, taxResidencyCountry, taxIdMasked, kycStatus, emailNotifications, smsNotifications, twoFactorEnabled);
	}

	/**
	 * The personal details still to fill in before identity verification can be submitted, by name; empty when
	 * complete. Address line 2 is optional.
	 */
	public List<String> missingPersonalDetails() {
		List<String> missing = new ArrayList<>();
		if (blank(phone)) {
			missing.add("mobile phone");
		}
		if (dateOfBirth == null) {
			missing.add("date of birth");
		}
		if (blank(nationality)) {
			missing.add("nationality");
		}
		if (address == null || blank(address.line1()) || blank(address.city()) || blank(address.stateRegion())
				|| blank(address.postalCode()) || blank(address.country())) {
			missing.add("address");
		}
		return missing;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	static String mask(String last4) {
		return last4 == null ? null : "•••• " + last4;
	}

}
