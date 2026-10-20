package com.sealease.backend.user.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.user.dto.NotificationPreferencesRequest;
import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.dto.UpdateProfileRequest;
import com.sealease.backend.user.dto.UpdateTaxInfoRequest;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.User;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.repository.UserProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Self-service profile management and the profile facts other modules depend on. */
@Service
public class UserProfileService {

	static final String TAX_ID_CONTEXT = "user_profile.tax_id";
	/** Nationality and address country when the investor does not choose one. */
	static final String DEFAULT_COUNTRY = "IN";
	private static final int MINIMUM_AGE = 18;

	private final UserProfileRepository profiles;
	private final FieldEncryptor encryptor;
	private final AuditService audit;
	private final Clock clock;

	public UserProfileService(UserProfileRepository profiles, FieldEncryptor encryptor, AuditService audit,
			Clock clock) {
		this.profiles = profiles;
		this.encryptor = encryptor;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	void createFor(User user) {
		profiles.save(new UserProfile(user));
	}

	/** A self-registered investor's profile, with the mobile number they signed up with (unique across accounts). */
	@Transactional(propagation = Propagation.MANDATORY)
	void createFor(User user, String phone) {
		String number = blankToNull(phone);
		String key = PhoneNumbers.key(number);
		if (key != null && profiles.phoneTakenByAnother(key, user.getId())) {
			throw phoneTaken();
		}
		UserProfile profile = new UserProfile(user);
		profile.updatePersonal(number, key, null, null);
		try {
			profiles.saveAndFlush(profile);
		}
		catch (DataIntegrityViolationException ex) {
			throw phoneTaken();
		}
	}

	@Transactional(readOnly = true)
	public ProfileResponse getProfile(UUID userId) {
		return ProfileResponse.from(load(userId));
	}

	@Transactional
	public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
		UserProfile profile = profiles.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		User user = profile.getUser();
		String firstName = request.firstName().strip();
		String lastName = request.lastName().strip();
		// The name is the one given at registration and cannot be changed afterwards.
		if (!firstName.equals(user.getFirstName()) || !lastName.equals(user.getLastName())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Your name cannot be changed after registration; contact support if it needs correcting");
		}
		if (request.dateOfBirth() != null
				&& request.dateOfBirth().isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusYears(MINIMUM_AGE))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You must be at least " + MINIMUM_AGE + " years old");
		}

		List<String> changed = new ArrayList<>();
		ProfileResponse before = ProfileResponse.from(profile);
		var address = request.address();
		String phone = blankToNull(request.phone());
		String phoneKey = PhoneNumbers.key(phone);
		if (phoneKey != null && profiles.phoneTakenByAnother(phoneKey, userId)) {
			throw phoneTaken();
		}

		profile.updatePersonal(phone, phoneKey, request.dateOfBirth(), orIndia(request.nationality()));
		profile.updateAddress(trim(address.line1()), trim(address.line2()), trim(address.city()),
				trim(address.stateRegion()), trim(address.postalCode()), orIndia(address.country()));
		try {
			// The unique index settles two accounts saving the same number at once.
			profiles.saveAndFlush(profile);
		}
		catch (DataIntegrityViolationException ex) {
			throw phoneTaken();
		}

		ProfileResponse after = ProfileResponse.from(profile);
		if (!Objects.equals(before.phone(), after.phone())) {
			changed.add("phone");
		}
		if (!Objects.equals(before.dateOfBirth(), after.dateOfBirth())
				|| !Objects.equals(before.nationality(), after.nationality())) {
			changed.add("personal");
		}
		if (!Objects.equals(before.address(), after.address())) {
			changed.add("address");
		}
		if (!changed.isEmpty()) {
			// Field names only: personal data does not belong in the audit log.
			audit.record(AuditRecord.of(userId, AuditAction.PROFILE_UPDATED, "USER", userId)
				.withNewValue(Map.of("changed", changed)));
		}
		return after;
	}

	/** Whether the user wants notifications by email; accounts without a profile (staff) get them. */
	@Transactional(readOnly = true)
	public boolean wantsEmailNotifications(UUID userId) {
		return profiles.findByUserId(userId).map(UserProfile::isEmailNotifications).orElse(true);
	}

	@Transactional
	public ProfileResponse updateNotificationPreferences(UUID userId, NotificationPreferencesRequest request) {
		UserProfile profile = profiles.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		boolean changed = profile.isEmailNotifications() != request.emailNotifications()
				|| profile.isSmsNotifications() != request.smsNotifications();
		profile.updateNotificationPreferences(request.emailNotifications(), request.smsNotifications());
		if (changed) {
			audit.record(AuditRecord.of(userId, AuditAction.PROFILE_UPDATED, "USER", userId)
				.withNewValue(Map.of("changed", List.of("notificationPreferences"))));
		}
		return ProfileResponse.from(profile);
	}

	@Transactional
	public ProfileResponse updateTax(UUID userId, UpdateTaxInfoRequest request) {
		UserProfile profile = profiles.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		String taxId = request.taxId().strip().toUpperCase();
		profile.updateTax(request.taxResidencyCountry(), encryptor.encrypt(taxId, TAX_ID_CONTEXT),
				FieldEncryptor.lastChars(taxId.replace(" ", "").replace("-", ""), 4));
		audit.record(AuditRecord.of(userId, AuditAction.TAX_INFO_UPDATED, "USER", userId)
			.withNewValue(Map.of("taxResidencyCountry", request.taxResidencyCountry(),
					"taxIdLast4", profile.getTaxIdLast4())));
		return ProfileResponse.from(profile);
	}

	/** Called by the KYC module inside its review/submission transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markKycStatus(UUID userId, KycStatus status) {
		profiles.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User", userId))
			.markKyc(status);
	}

	@Transactional(readOnly = true)
	public KycStatus kycStatusOf(UUID userId) {
		return load(userId).getKycStatus();
	}

	private UserProfile load(UUID userId) {
		return profiles.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
	}

	private static BusinessException phoneTaken() {
		return new BusinessException(ErrorCode.CONFLICT,
				"This mobile number is already registered with another account. Contact support if it is yours.");
	}

	private static String orIndia(String country) {
		return country == null || country.isBlank() ? DEFAULT_COUNTRY : country;
	}

	private static String trim(String value) {
		return blankToNull(value);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
