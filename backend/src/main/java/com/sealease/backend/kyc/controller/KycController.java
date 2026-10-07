package com.sealease.backend.kyc.controller;

import com.sealease.backend.kyc.dto.KycFiles;
import com.sealease.backend.kyc.dto.KycSubmissionRequest;
import com.sealease.backend.kyc.dto.KycSubmissionResponse;
import com.sealease.backend.kyc.service.KycService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/kyc")
public class KycController {

	private final KycService kyc;

	public KycController(KycService kyc) {
		this.kyc = kyc;
	}

	/** Latest submission, or 204 when the user has never submitted. */
	@GetMapping
	public ResponseEntity<KycSubmissionResponse> latest(AuthenticatedUser user) {
		return kyc.latestFor(user.userId()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	/**
	 * Multipart: a JSON part {@code submission} plus file parts {@code identityFront},
	 * {@code identityBack}, {@code selfie} and optionally {@code proofOfAddress}.
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public KycSubmissionResponse submit(AuthenticatedUser user,
			@Valid @RequestPart("submission") KycSubmissionRequest submission,
			@RequestPart(value = "identityFront", required = false) MultipartFile identityFront,
			@RequestPart(value = "identityBack", required = false) MultipartFile identityBack,
			@RequestPart(value = "selfie", required = false) MultipartFile selfie,
			@RequestPart(value = "proofOfAddress", required = false) MultipartFile proofOfAddress) {
		return kyc.submit(user.userId(), submission, new KycFiles(identityFront, identityBack, selfie, proofOfAddress));
	}

}
