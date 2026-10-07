package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;

/** Optional filters for the staff user directory; {@code q} matches email or name. */
public record UserSearchCriteria(String q, UserStatus status, KycStatus kycStatus) {
}
