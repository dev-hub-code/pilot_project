package com.sealease.backend.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Moves a member, with everyone below them, to another referrer, or out of their downline when {@code referrerId} is
 * {@code null}; the reason is audited.
 */
public record MoveReferralRequest(UUID referrerId, @NotBlank @Size(max = 500) String reason) {
}
