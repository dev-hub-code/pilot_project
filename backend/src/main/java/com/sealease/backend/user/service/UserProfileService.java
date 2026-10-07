package com.sealease.backend.user.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.dto.UpdateProfileRequest;
import com.sealease.backend.user.dto.UpdateTaxInfoRequest;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.User;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.repository.UserProfileRepository;
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
		boolean renaming = !firstName.equals(user.getFirstName()) || !lastName.equals(user.getLastName());
		if (renaming && profile.getKycStatus() != KycStatus.NOT_SUBMITTED
				&& profile.getKycStatus() != KycStatus.REJECTED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Your name is locked while identity verification is pending or approved; contact support to change it");
		}
		if (request.dateOfBirth() != null
				&& request.dateOfBirth().isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusYears(MINIMUM_AGE))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You must be at least " + MINIMUM_AGE + " years old");
		}

		List<String> changed = new ArrayList<>();
		if (renaming) {
			changed.add("name");
		}
		ProfileResponse before = ProfileResponse.from(profile);
		var address = request.address() == null
				? new UpdateProfileRequest.AddressRequest(null, null, null, null, null, null) : request.address();

		user.rename(firstName, lastName);
		profile.updatePersonal(blankToNull(request.phone()), request.dateOfBirth(), request.nationality());
		profile.updateAddress(trim(address.line1()), trim(address.line2()), trim(address.city()),
				trim(address.stateRegion()), trim(address.postalCode()), address.country());
		profile.updateNotificationPreferences(request.emailNotifications(), request.smsNotifications());

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
		if (before.emailNotifications() != after.emailNotifications()
				|| before.smsNotifications() != after.smsNotifications()) {
			changed.add("notificationPreferences");
		}
		if (!changed.isEmpty()) {
			// Field names only: personal data does not belong in the audit log.
			audit.record(AuditRecord.of(userId, AuditAction.PROFILE_UPDATED, "USER", userId)
				.withNewValue(Map.of("changed", changed)));
		}
		return after;
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

	private static String trim(String value) {
		return blankToNull(value);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
