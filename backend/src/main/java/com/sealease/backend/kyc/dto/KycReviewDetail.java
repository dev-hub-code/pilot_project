package com.sealease.backend.kyc.dto;

import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.dto.UserAccount;

/**
 * Reviewer view: the submission next to the account it belongs to, for name comparison, and the investor's address
 * as their profile holds it now (null when they have none).
 */
public record KycReviewDetail(KycSubmissionResponse submission, UserAccount account, ProfileResponse.Address address) {
}
