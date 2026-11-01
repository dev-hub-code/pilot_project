package com.sealease.backend.auth.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A network investor made by an administrator.
 *
 * @param referralCode       the code of whoever referred them, if anyone
 * @param signupsJoinNetwork whether people who sign up with this investor's code become network investors too
 */
@ValidPhone
public record CreateInvestorRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank(message = "Enter their mobile number") @Size(max = 30) String phone,
		@IsoCountry String phoneCountry,
		@Pattern(regexp = "^([2-9A-HJ-NP-Z]{8})?$", message = "must be 8 capital letters and digits")
		String referralCode,
		@NotNull Boolean signupsJoinNetwork) implements ValidPhone.Input {
}
