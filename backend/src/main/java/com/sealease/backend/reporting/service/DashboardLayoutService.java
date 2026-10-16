package com.sealease.backend.reporting.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.reporting.dto.DashboardLayoutResponse;
import com.sealease.backend.reporting.entity.DashboardLayout;
import com.sealease.backend.reporting.repository.DashboardLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Each employee's own arrangement of the overview. Only the order and visibility of cards is kept; which cards exist
 * and what they hold is up to the overview and the viewer's permissions, so unknown keys are simply not shown. The
 * cards their roles allow come with it (see {@link RoleDashboardCardService}).
 */
@Service
public class DashboardLayoutService {

	private static final DashboardLayoutResponse DEFAULT = new DashboardLayoutResponse(List.of(), List.of(), null, null);

	private final DashboardLayoutRepository layouts;
	private final RoleDashboardCardService roleCards;
	private final Clock clock;

	public DashboardLayoutService(DashboardLayoutRepository layouts, RoleDashboardCardService roleCards, Clock clock) {
		this.layouts = layouts;
		this.roleCards = roleCards;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardLayoutResponse get(UUID userId) {
		return layouts.findById(userId).map(DashboardLayoutService::response).orElse(DEFAULT)
			.withAllowed(roleCards.allowedFor(userId).orElse(null));
	}

	@Transactional
	public DashboardLayoutResponse save(UUID userId, List<String> cards, List<String> hidden) {
		Set<String> seen = new HashSet<>();
		for (String key : cards) {
			requireOnce(seen, key);
		}
		for (String key : hidden) {
			requireOnce(seen, key);
		}
		DashboardLayout layout = layouts.findById(userId).orElseGet(() -> new DashboardLayout(userId));
		layout.arrange(cards, hidden, clock.instant());
		layouts.saveAndFlush(layout);
		return get(userId);
	}

	/** Back to the default layout. */
	@Transactional
	public DashboardLayoutResponse reset(UUID userId) {
		layouts.deleteById(userId);
		return get(userId);
	}

	private static void requireOnce(Set<String> seen, String key) {
		if (!seen.add(key)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Each card can be placed only once: " + key);
		}
	}

	private static DashboardLayoutResponse response(DashboardLayout layout) {
		return new DashboardLayoutResponse(layout.getCards(), layout.getHidden(), layout.getUpdatedAt(), null);
	}

}
