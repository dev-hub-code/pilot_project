package com.sealease.backend.kyc.dto;

import jakarta.validation.constraints.Size;

/**
 * The investor's Aadhaar number, PAN, or both (checked in the service), each uploaded with its card: they may come at
 * different times. Name, date of birth and nationality are taken from the investor's profile.
 *
 * @param aadhaarNumber the 12-digit Aadhaar number; spaces and dashes are ignored; blank when not uploading it now
 * @param panNumber     the PAN, e.g. ABCDE1234F; case and spaces are ignored; blank when not uploading it now
 */
public record KycSubmissionRequest(
		@Size(max = 20) String aadhaarNumber,
		@Size(max = 20) String panNumber) {

	@Override
	public String toString() {
		return "KycSubmissionRequest[aadhaarNumber=***, panNumber=***]";
	}

}
