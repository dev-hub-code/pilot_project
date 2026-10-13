package com.sealease.backend.user.dto;

import com.sealease.backend.common.validation.IsoCountry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Full replacement of the editable profile fields (PUT semantics); notification preferences are set separately.
 * The mobile number is unique across accounts; nationality and the address's country are India when not given.
 */
public record UpdateProfileRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@Pattern(regexp = "^[+0-9 ()-]{6,20}$", message = "must be a phone number (digits, spaces, +, brackets or dashes)")
		String phone,
		@Past LocalDate dateOfBirth,
		@IsoCountry String nationality,
		@NotNull(message = "is required") @Valid AddressRequest address,
		// Optional GSTIN: 2-digit state code, the PAN, an entity digit, Z and a check character.
		@Pattern(regexp = "^\\s*$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$",
				message = "must be a 15-character GSTIN, e.g. 27ABCDE1234F1Z5") String gstNumber) {

	public record AddressRequest(
			@NotBlank(message = "Enter the first line of your address") @Size(max = 200) String line1,
			@Size(max = 200) String line2,
			@NotBlank(message = "Enter your city") @Size(max = 100) String city,
			@NotBlank(message = "Enter your state") @Size(max = 100) String stateRegion,
			@NotBlank(message = "Enter your postal code") @Size(max = 20) String postalCode,
			@IsoCountry String country) {
	}

}
