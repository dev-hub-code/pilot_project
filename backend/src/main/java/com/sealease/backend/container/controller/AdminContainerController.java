package com.sealease.backend.container.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.container.dto.ContainerDetail;
import com.sealease.backend.container.dto.ContainerDocumentResponse;
import com.sealease.backend.container.dto.ContainerRequest;
import com.sealease.backend.container.dto.ContainerSearchCriteria;
import com.sealease.backend.container.dto.ContainerStatusRequest;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.container.service.ContainerService;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/containers")
public class AdminContainerController {

	private final ContainerService containers;

	public AdminContainerController(ContainerService containers) {
		this.containers = containers;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public PageResponse<ContainerDetail> search(@RequestParam(required = false) @Size(max = 50) String q,
			@RequestParam(required = false) ContainerStatus status,
			@RequestParam(required = false) ContainerType containerType,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(containers.search(new ContainerSearchCriteria(q, status, containerType), pageable));
	}

	@GetMapping("/{containerId}")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public ContainerDetail detail(@PathVariable UUID containerId) {
		return containers.detail(containerId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('INVESTMENT_CREATE')")
	public ContainerDetail create(AuthenticatedUser actor, @Valid @RequestBody ContainerRequest request) {
		return containers.create(actor.userId(), request);
	}

	@PutMapping("/{containerId}")
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public ContainerDetail update(AuthenticatedUser actor, @PathVariable UUID containerId,
			@Valid @RequestBody ContainerRequest request) {
		return containers.update(actor.userId(), containerId, request);
	}

	@PostMapping("/{containerId}/status")
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public ContainerDetail changeStatus(AuthenticatedUser actor, @PathVariable UUID containerId,
			@Valid @RequestBody ContainerStatusRequest request) {
		return containers.changeStatus(actor.userId(), containerId, request.status(), request.reason());
	}

	@PostMapping(path = "/{containerId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public ContainerDocumentResponse upload(AuthenticatedUser actor, @PathVariable UUID containerId,
			@RequestParam DocumentPurpose purpose, @RequestParam @NotBlank @Size(max = 140) String title,
			@RequestParam(defaultValue = "false") boolean visibleToInvestors, @RequestPart("file") MultipartFile file) {
		return containers.addDocument(actor.userId(), containerId, purpose, title, visibleToInvestors, file);
	}

	@PatchMapping("/{containerId}/documents/{documentId}")
	@PreAuthorize("hasAuthority('INVESTMENT_UPDATE')")
	public ContainerDocumentResponse setVisibility(AuthenticatedUser actor, @PathVariable UUID containerId,
			@PathVariable UUID documentId, @RequestBody VisibilityRequest request) {
		return containers.setDocumentVisibility(actor.userId(), containerId, documentId, request.visibleToInvestors());
	}

	@GetMapping("/{containerId}/documents/{documentId}")
	@PreAuthorize("hasAuthority('INVESTMENT_VIEW')")
	public ResponseEntity<byte[]> document(@PathVariable UUID containerId, @PathVariable UUID documentId) {
		return DocumentResponses.inline(containers.document(containerId, documentId));
	}

	public record VisibilityRequest(boolean visibleToInvestors) {
	}

}
