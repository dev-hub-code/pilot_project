package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;

/**
 * Optional filters for the staff user directory; {@code q} matches email or name, or is a readable ID ("SL03500").
 *
 * @param emailVerified only accounts whose email address is (true) or is not yet (false) verified; null for both
 * @param kind          only investors or only employees; null for both
 * @param investorsOnly only investors' accounts, for a viewer who may not see employees
 */
public record UserSearchCriteria(String q, UserStatus status, KycStatus kycStatus, Boolean emailVerified, UserKind kind,
		boolean investorsOnly) {
}
