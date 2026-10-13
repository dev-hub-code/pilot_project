package com.sealease.backend.kyc.dto;

import org.springframework.web.multipart.MultipartFile;

/** The uploaded documents: the Aadhaar card (one file with both sides), the PAN card, or both; each with its number. */
public record KycFiles(MultipartFile aadhaarCard, MultipartFile panCard) {
}
