package com.sealease.backend.helpdesk.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.helpdesk.dto.OpenTicketRequest;
import com.sealease.backend.helpdesk.dto.TicketDetail;
import com.sealease.backend.helpdesk.dto.TicketResponse;
import com.sealease.backend.helpdesk.service.SupportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Investors' own support tickets. Messages are multipart so files can be attached. */
@RestController
@Validated
@RequestMapping("/api/v1/support/tickets")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class SupportController {

	private final SupportService support;

	public SupportController(SupportService support) {
		this.support = support;
	}

	@GetMapping
	public PageResponse<TicketResponse> mine(AuthenticatedUser investor, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(support.mine(investor.userId(), pageable));
	}

	@GetMapping("/{ticketId}")
	public TicketDetail detail(AuthenticatedUser investor, @PathVariable UUID ticketId) {
		return support.customerDetail(investor.userId(), ticketId);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TicketDetail open(AuthenticatedUser investor, @Valid @ModelAttribute OpenTicketRequest request,
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		return support.open(investor.userId(), request, files);
	}

	@PostMapping(path = "/{ticketId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TicketDetail reply(AuthenticatedUser investor, @PathVariable UUID ticketId,
			@RequestParam @NotBlank @Size(max = 8000) String body,
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		return support.customerReply(investor.userId(), ticketId, body, files);
	}

	@PostMapping("/{ticketId}/close")
	public TicketDetail close(AuthenticatedUser investor, @PathVariable UUID ticketId) {
		return support.customerClose(investor.userId(), ticketId);
	}

	@GetMapping("/{ticketId}/attachments/{attachmentId}")
	public ResponseEntity<byte[]> attachment(AuthenticatedUser investor, @PathVariable UUID ticketId,
			@PathVariable UUID attachmentId) {
		return DocumentResponses.inline(support.customerAttachment(investor.userId(), ticketId, attachmentId));
	}

}
