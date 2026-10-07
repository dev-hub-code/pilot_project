package com.sealease.backend.referral.dto;

import java.util.List;

/** @param truncated the downline is larger than the response limit; only the earliest members are listed */
public record Downline(List<DownlineMember> members, boolean truncated) {
}
