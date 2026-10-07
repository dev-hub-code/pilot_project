package com.sealease.backend.user.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.user.dto.AdminUserDetail;
import com.sealease.backend.user.dto.AdminUserSummary;
import com.sealease.backend.user.dto.UserSearchCriteria;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.User;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.event.UserStatusChangedEvent;
import com.sealease.backend.user.repository.UserProfileRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Staff operations on accounts: directory search and suspension. */
@Service
public class UserAdministrationService {

	private final UserProfileRepository profiles;
	private final AuthorityGuard authorityGuard;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public UserAdministrationService(UserProfileRepository profiles,
			AuthorityGuard authorityGuard, AuditService audit, ApplicationEventPublisher events, Clock clock) {
		this.profiles = profiles;
		this.authorityGuard = authorityGuard;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<AdminUserSummary> search(UserSearchCriteria criteria, Pageable pageable) {
		return profiles.findAll(matching(criteria), pageable).map(AdminUserSummary::from);
	}

	@Transactional(readOnly = true)
	public AdminUserDetail detail(UUID userId) {
		UserProfile profile = profiles.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
		return AdminUserDetail.from(profile);
	}

	@Transactional
	public AdminUserDetail suspend(UUID actorId, UUID userId, String reason) {
		return changeStatus(actorId, userId, UserStatus.ACTIVE, UserStatus.SUSPENDED, reason, AuditAction.USER_SUSPENDED);
	}

	@Transactional
	public AdminUserDetail reactivate(UUID actorId, UUID userId, String reason) {
		return changeStatus(actorId, userId, UserStatus.SUSPENDED, UserStatus.ACTIVE, reason,
				AuditAction.USER_REACTIVATED);
	}

	private AdminUserDetail changeStatus(UUID actorId, UUID userId, UserStatus from, UserStatus to, String reason,
			AuditAction action) {
		requireNotSelf(actorId, userId);
		authorityGuard.requireActorCovers(actorId, userId);
		UserProfile profile = profiles.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		User user = profile.getUser();
		if (user.getStatus() != from) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Account is " + user.getStatus() + "; expected " + from);
		}
		user.changeStatus(to, reason.strip(), actorId, clock.instant());
		audit.record(AuditRecord.of(actorId, action, "USER", userId)
			.withOldValue(Map.of("status", from))
			.withNewValue(Map.of("status", to, "reason", reason.strip())));
		// Listeners (session revocation) run in this transaction.
		events.publishEvent(new UserStatusChangedEvent(userId, from, to));
		return detail(userId);
	}

	private static void requireNotSelf(UUID actorId, UUID userId) {
		if (actorId.equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot perform this action on your own account");
		}
	}

	private static Specification<UserProfile> matching(UserSearchCriteria criteria) {
		return (root, query, cb) -> {
			// Fetch-join for the page query (no N+1); plain join for the count query.
			boolean countQuery = query != null && Long.class == query.getResultType();
			@SuppressWarnings("unchecked")
			Join<UserProfile, User> user = countQuery ? root.join("user")
					: (Join<UserProfile, User>) root.<UserProfile, User>fetch("user");
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.q() != null && !criteria.q().isBlank()) {
				String like = "%" + escapeLike(criteria.q().strip().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(
						cb.like(user.get("email"), like, '\\'),
						cb.like(cb.lower(user.get("firstName")), like, '\\'),
						cb.like(cb.lower(user.get("lastName")), like, '\\')));
			}
			if (criteria.status() != null) {
				predicates.add(cb.equal(user.get("status"), criteria.status()));
			}
			if (criteria.kycStatus() != null) {
				predicates.add(cb.equal(root.get("kycStatus"), criteria.kycStatus()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
