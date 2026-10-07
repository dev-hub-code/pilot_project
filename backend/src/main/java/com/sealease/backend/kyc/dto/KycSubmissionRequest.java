package com.sealease.backend.kyc.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.kyc.entity.IdentityDocumentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record KycSubmissionRequest(
		@NotBlank @Size(max = 100) String legalFirstName,
		@NotBlank @Size(max = 100) String legalLastName,
		@NotNull @Past LocalDate dateOfBirth,
		@NotBlank @IsoCountry String nationality,
		@NotNull IdentityDocumentType documentType,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{4,30}$", message = "must be 4-30 letters, digits, spaces or dashes")
		String documentNumber,
		@NotBlank @IsoCountry String documentIssuingCountry,
		@NotNull @Future(message = "document must not be expired") LocalDate documentExpiryDate) {

	@Override
	public String toString() {
		return "KycSubmissionRequest[documentType=" + documentType + ", documentNumber=***]";
	}

}
