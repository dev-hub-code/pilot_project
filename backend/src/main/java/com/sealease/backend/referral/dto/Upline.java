package com.sealease.backend.referral.dto;

import java.util.UUID;

/** An ancestor of an account in the referral hierarchy; level 1 is the direct referrer. */
public record Upline(UUID userId, int level) {
}
