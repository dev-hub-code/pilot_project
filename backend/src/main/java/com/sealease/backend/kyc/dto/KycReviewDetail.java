package com.sealease.backend.kyc.dto;

import com.sealease.backend.user.dto.UserAccount;

/** Reviewer view: the submission next to the account it belongs to, for name comparison. */
public record KycReviewDetail(KycSubmissionResponse submission, UserAccount account) {
}
