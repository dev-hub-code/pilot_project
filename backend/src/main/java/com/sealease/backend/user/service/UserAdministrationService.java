package com.sealease.backend.user.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.role.entity.Role;
import com.sealease.backend.role.entity.UserRole;
import com.sealease.backend.user.dto.AdminUserDetail;
import com.sealease.backend.user.dto.AdminUserSummary;
import com.sealease.backend.user.dto.UserKind;
import com.sealease.backend.user.dto.UserSearchCriteria;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.User;
import com.sealease.backend.user.entity.UserCodes;
import com.sealease.backend.user.entity.UserProfile;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.event.UserStatusChangedEvent;
import com.sealease.backend.user.repository.UserProfileRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
		Page<UserProfile> page = profiles.findAll(matching(criteria), pageable);
		// Which accounts on the page are investors, in one query (the rest are employees).
		List<UUID> ids = page.getContent().stream().map(p -> p.getUser().getId()).toList();
		Set<UUID> investors = ids.isEmpty() ? Set.of() : profiles.findAll((root, query, cb) -> cb.and(
				root.get("user").get("id").in(ids), investor(root.get("user"), query, cb))).stream()
			.map(p -> p.getUser().getId()).collect(Collectors.toSet());
		return page.map(p -> AdminUserSummary.from(p,
				investors.contains(p.getUser().getId()) ? UserKind.INVESTOR : UserKind.EMPLOYEE));
	}

	@Transactional(readOnly = true)
	public AdminUserDetail detail(UUID userId) {
		UserProfile profile = profiles.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
		boolean investor = profiles.count((root, query, cb) -> cb.and(
				cb.equal(root.get("user").get("id"), userId), investor(root.get("user"), query, cb))) > 0;
		return AdminUserDetail.from(profile, investor ? UserKind.INVESTOR : UserKind.EMPLOYEE);
	}

	/** One account; with {@code investorsOnly}, a staff account is not found. */
	@Transactional(readOnly = true)
	public AdminUserDetail detail(UUID userId, boolean investorsOnly) {
		if (investorsOnly && profiles.count((root, query, cb) -> cb.and(
				cb.equal(root.get("user").get("id"), userId), investor(root.get("user"), query, cb))) == 0) {
			throw new ResourceNotFoundException("User", userId);
		}
		return detail(userId);
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
			Optional<Long> number = UserCodes.parse(criteria.q());
			if (number.isPresent()) {
				// A readable ID ("SL03500") finds that one account.
				predicates.add(cb.equal(user.get("userNumber"), number.get()));
			}
			else if (criteria.q() != null && !criteria.q().isBlank()) {
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
			if (criteria.emailVerified() != null) {
				Path<Instant> verifiedAt = user.get("emailVerifiedAt");
				predicates.add(criteria.emailVerified() ? cb.isNotNull(verifiedAt) : cb.isNull(verifiedAt));
			}
			if (criteria.investorsOnly() || criteria.kind() == UserKind.INVESTOR) {
				predicates.add(investor(user, query, cb));
			}
			else if (criteria.kind() == UserKind.EMPLOYEE) {
				predicates.add(cb.not(investor(user, query, cb)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** The user is an investor: one of their roles grants the investor portal. */
	private static Predicate investor(Path<User> user, CriteriaQuery<?> query, CriteriaBuilder cb) {
		Subquery<UUID> investors = query.subquery(UUID.class);
		Root<UserRole> userRole = investors.from(UserRole.class);
		Join<Role, Permission> permission = userRole.join("role").join("permissions");
		investors.select(userRole.get("id").get("userId"))
			.where(cb.equal(permission.get("code"), PermissionCode.INVESTOR_PORTAL.name()));
		return user.get("id").in(investors);
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
