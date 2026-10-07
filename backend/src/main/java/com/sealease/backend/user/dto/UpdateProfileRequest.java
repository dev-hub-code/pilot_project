package com.sealease.backend.user.dto;

import com.sealease.backend.common.validation.IsoCountry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Full replacement of the editable profile fields (PUT semantics). */
public record UpdateProfileRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in international format, e.g. +14155550123")
		String phone,
		@Past LocalDate dateOfBirth,
		@IsoCountry String nationality,
		@Valid AddressRequest address,
		@NotNull Boolean emailNotifications,
		@NotNull Boolean smsNotifications) {

	public record AddressRequest(
			@Size(max = 200) String line1,
			@Size(max = 200) String line2,
			@Size(max = 100) String city,
			@Size(max = 100) String stateRegion,
			@Size(max = 20) String postalCode,
			@IsoCountry String country) {
	}

}
