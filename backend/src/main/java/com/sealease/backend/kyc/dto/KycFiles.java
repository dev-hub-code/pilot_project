package com.sealease.backend.kyc.dto;

import org.springframework.web.multipart.MultipartFile;

/** Uploaded identity evidence. Back image is required for ID cards and licences; proof of address is optional. */
public record KycFiles(MultipartFile identityFront, MultipartFile identityBack, MultipartFile selfie,
		MultipartFile proofOfAddress) {
}
