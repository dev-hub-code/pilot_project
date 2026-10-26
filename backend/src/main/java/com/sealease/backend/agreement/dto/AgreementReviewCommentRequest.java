package com.sealease.backend.agreement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgreementReviewCommentRequest(@NotBlank @Size(max = 2000) String body) {
}
