package com.sealease.backend.investment.controller;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.investment.dto.PlanPhotoResponse;
import com.sealease.backend.investment.service.PlanPhotoService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** A plan's marketplace photos. */
@RestController
@RequestMapping("/api/v1/admin/investment-products/{productId}/photos")
public class AdminPlanPhotoController {

	private final PlanPhotoService photos;

	public AdminPlanPhotoController(PlanPhotoService photos) {
		this.photos = photos;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public List<PlanPhotoResponse> list(@PathVariable UUID productId) {
		return photos.list(productId);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public PlanPhotoResponse add(AuthenticatedUser actor, @PathVariable UUID productId, @RequestPart("file") MultipartFile file) {
		return photos.add(actor.userId(), productId, file);
	}

	@DeleteMapping("/{photoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public void remove(AuthenticatedUser actor, @PathVariable UUID productId, @PathVariable UUID photoId) {
		photos.remove(actor.userId(), productId, photoId);
	}

	/** The image, by its document id (as listed). */
	@GetMapping("/{documentId}/content")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public ResponseEntity<byte[]> content(@PathVariable UUID productId, @PathVariable UUID documentId) {
		return DocumentResponses.inline(photos.photo(productId, documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Photo", documentId)));
	}

}
