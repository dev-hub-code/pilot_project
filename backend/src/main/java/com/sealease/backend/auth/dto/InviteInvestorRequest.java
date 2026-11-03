package com.sealease.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An employee invites a client to open a network investor account. The name greets them in the email and fills in the
 * form; they can correct it there.
 *
 * @param signupsJoinNetwork whether people who sign up with this investor's code become network investors too
 */
public record InviteInvestorRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotNull Boolean signupsJoinNetwork) {
}
