package com.sealease.backend.agreement.dto;

import jakarta.validation.constraints.Size;

/** @param comment a closing comment, added to the task's comments; optional */
public record AgreementReviewCompleteRequest(@Size(max = 2000) String comment) {
}
