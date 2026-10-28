package com.sealease.backend.demo.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A dummy downline member.
 *
 * @param parentId the member who referred them; {@code null} when the demo investor did (level 1)
 * @param joinedOn within the account's time frame, and not before their referrer joined
 */
public record DemoMemberRequest(UUID parentId, @NotBlank @Size(max = 120) String name, @NotNull LocalDate joinedOn) {
}
