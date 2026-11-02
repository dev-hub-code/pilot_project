package com.sealease.backend.announcement.controller;

import com.sealease.backend.announcement.dto.AnnouncementRequest;
import com.sealease.backend.announcement.dto.AnnouncementResponse;
import com.sealease.backend.announcement.service.AnnouncementService;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Announcements: send one to everyone, investors (all, online or network) or staff, and see those already sent. */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
public class AdminAnnouncementController {

	private final AnnouncementService announcements;

	public AdminAnnouncementController(AnnouncementService announcements) {
		this.announcements = announcements;
	}

	@GetMapping
	public PageResponse<AnnouncementResponse> list(@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(announcements.list(pageable));
	}

	/** Sends it at once, as a notification to every active account in the audience. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AnnouncementResponse publish(AuthenticatedUser actor, @Valid @RequestBody AnnouncementRequest request) {
		return announcements.publish(actor.userId(), request);
	}

}
