package com.sealease.backend.notification.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.notification.dto.NotificationResponse;
import com.sealease.backend.notification.dto.UnreadCount;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Every signed-in user's own notifications. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	public PageResponse<NotificationResponse> mine(AuthenticatedUser user, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(notifications.mine(user.userId(), pageable));
	}

	@GetMapping("/unread-count")
	public UnreadCount unread(AuthenticatedUser user) {
		return new UnreadCount(notifications.unread(user.userId()));
	}

	@PostMapping("/{notificationId}/read")
	public NotificationResponse read(AuthenticatedUser user, @PathVariable UUID notificationId) {
		return notifications.markRead(user.userId(), notificationId);
	}

	@PostMapping("/read-all")
	public UnreadCount readAll(AuthenticatedUser user) {
		notifications.markAllRead(user.userId());
		return new UnreadCount(0);
	}

}
