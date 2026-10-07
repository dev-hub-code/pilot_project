package com.sealease.backend.user.dto;

import com.sealease.backend.common.validation.IsoCountry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTaxInfoRequest(
		@NotBlank @IsoCountry String taxResidencyCountry,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{4,30}$", message = "must be 4-30 letters, digits, spaces or dashes")
		String taxId) {

	@Override
	public String toString() {
		return "UpdateTaxInfoRequest[taxResidencyCountry=" + taxResidencyCountry + ", taxId=***]";
	}

}
