package com.sealease.backend.reporting.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditCategory;
import com.sealease.backend.audit.AuditLogQuery;
import com.sealease.backend.audit.AuditLogQuery.ActorCount;
import com.sealease.backend.audit.AuditLogQuery.Bucket;
import com.sealease.backend.audit.AuditLogQuery.KeyCount;
import com.sealease.backend.audit.AuditLogQuery.PeriodCount;
import com.sealease.backend.auth.service.SessionService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.reporting.dto.SecurityOverview;
import com.sealease.backend.reporting.dto.SecurityOverview.Activity;
import com.sealease.backend.reporting.dto.SecurityOverview.FailedByAccount;
import com.sealease.backend.reporting.dto.SecurityOverview.FailedByIp;
import com.sealease.backend.reporting.dto.SecurityOverview.LockedAccount;
import com.sealease.backend.reporting.dto.SecurityOverview.StaffAccess;
import com.sealease.backend.reporting.dto.SecurityOverview.Totals;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The security dashboard: sign-in activity and attacks, accounts locked and sessions open now, staff
 * access to personal data, and changes to who may do what. Built from the audit log, so it covers
 * exactly what the log records (failed sign-ins are recorded for existing accounts only).
 */
@Service
public class SecurityDashboardService {

	/** The periods the dashboard offers, in hours. */
	public static final Set<Integer> PERIODS = Set.of(24, 24 * 7, 24 * 30, 24 * 90);
	private static final int TOP = 8;
	private static final int RECENT = 10;

	private static final Set<AuditAction> SIGN_IN_ACTIVITY = EnumSet.of(AuditAction.LOGIN, AuditAction.LOGIN_FAILED);
	private static final Set<AuditAction> TOTALS = EnumSet.of(AuditAction.LOGIN, AuditAction.LOGIN_FAILED,
			AuditAction.ACCOUNT_LOCKED, AuditAction.TOKEN_REFRESH_REUSE_DETECTED, AuditAction.PASSWORD_RESET,
			AuditAction.PASSWORD_CHANGE);
	/** Changes to access and to platform-wide settings that move money. */
	private static final Set<AuditAction> PRIVILEGED;
	private static final Set<AuditAction> SECURITY_EVENTS = EnumSet.of(AuditAction.ACCOUNT_LOCKED,
			AuditAction.TOKEN_REFRESH_REUSE_DETECTED, AuditAction.PASSWORD_RESET, AuditAction.PASSWORD_CHANGE,
			AuditAction.LOGOUT_ALL, AuditAction.TEMPORARY_PASSWORD_ISSUED);

	static {
		Set<AuditAction> privileged = AuditCategory.ACCESS_CONTROL.actions();
		privileged.addAll(EnumSet.of(AuditAction.TEMPORARY_PASSWORD_ISSUED, AuditAction.PAYOUT_DAY_CHANGED,
				AuditAction.WITHDRAWAL_DAY_CHANGED, AuditAction.REFERRAL_RATES_SCHEDULED,
				AuditAction.REFERRAL_RATES_CANCELLED, AuditAction.COMPANY_BANK_ACCOUNT_CREATED,
				AuditAction.COMPANY_BANK_ACCOUNT_UPDATED, AuditAction.COMPANY_BANK_ACCOUNT_ACTIVATED,
				AuditAction.COMPANY_BANK_ACCOUNT_DEACTIVATED, AuditAction.LEDGER_ADJUSTMENT_POSTED,
				AuditAction.REFERRAL_MOVED));
		PRIVILEGED = privileged;
	}

	private final AuditLogQuery query;
	private final AuditLogService log;
	private final UserAccountService accounts;
	private final SessionService sessions;
	private final Clock clock;

	public SecurityDashboardService(AuditLogQuery query, AuditLogService log, UserAccountService accounts,
			SessionService sessions, Clock clock) {
		this.query = query;
		this.log = log;
		this.accounts = accounts;
		this.sessions = sessions;
		this.clock = clock;
	}

	/** Failed sign-ins in the last 24 hours, for the admin overview. */
	@Transactional(readOnly = true)
	public long failedSignInsLastDay() {
		Instant to = clock.instant();
		return query.countByAction(Set.of(AuditAction.LOGIN_FAILED), to.minus(Duration.ofHours(24)), to)
			.get(AuditAction.LOGIN_FAILED);
	}

	@Transactional(readOnly = true)
	public SecurityOverview overview(int hours) {
		if (!PERIODS.contains(hours)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "hours must be 24, 168, 720 or 2160");
		}
		Instant to = clock.instant();
		Instant from = to.minus(Duration.ofHours(hours));

		Map<AuditAction, Long> counts = query.countByAction(TOTALS, from, to);
		SessionService.ActiveSessions active = sessions.activeSessions();
		List<ActorCount> access = query.countByActor(AuditCategory.SENSITIVE_DATA.actions(), from, to);
		long privilegedCount = query.countByAction(PRIVILEGED, from, to).values().stream().mapToLong(Long::longValue).sum();

		List<UserAccountService.LockedAccount> locked = accounts.lockedAccounts();
		List<KeyCount> failedAccounts = query.topEntities(AuditAction.LOGIN_FAILED, from, to, TOP);

		// Everyone named on the page, looked up at once.
		Set<UUID> people = new HashSet<>();
		access.forEach(a -> people.add(a.actorUserId()));
		failedAccounts.forEach(k -> uuid(k.key()).ifPresent(people::add));
		Map<UUID, UserAccount> named = people.isEmpty() ? Map.of() : accounts.getAccounts(people);

		Bucket bucket = hours <= 24 ? Bucket.HOUR : Bucket.DAY;
		return new SecurityOverview(hours, from, to,
				new Totals(counts.get(AuditAction.LOGIN), counts.get(AuditAction.LOGIN_FAILED),
						counts.get(AuditAction.ACCOUNT_LOCKED), counts.get(AuditAction.TOKEN_REFRESH_REUSE_DETECTED),
						counts.get(AuditAction.PASSWORD_RESET), counts.get(AuditAction.PASSWORD_CHANGE),
						active.sessions(), active.users(), access.stream().mapToLong(ActorCount::count).sum(),
						privilegedCount),
				bucket.name(),
				activity(query.countByPeriod(SIGN_IN_ACTIVITY, from, to, bucket), from, to, bucket),
				locked.stream().map(l -> new LockedAccount(AuditLogService.ref(l.account().id(),
						Map.of(l.account().id(), l.account())), l.lockedUntil(), l.failedAttempts())).toList(),
				failedAccounts.stream().flatMap(k -> uuid(k.key()).stream()
					.map(id -> new FailedByAccount(AuditLogService.ref(id, named), k.count(), k.latest()))).toList(),
				query.topIpAddresses(Set.of(AuditAction.LOGIN_FAILED), from, to, TOP).stream()
					.map(k -> new FailedByIp(k.key(), k.count(), k.latest())).toList(),
				staffAccess(access, named),
				List.copyOf(log.toResponses(query.recent(PRIVILEGED, from, to, RECENT)).values()),
				List.copyOf(log.toResponses(query.recent(SECURITY_EVENTS, from, to, RECENT)).values()));
	}

	/** One entry per hour or day from the one holding {@code from} up to {@code to}, quiet ones included. */
	private static List<Activity> activity(List<PeriodCount> counts, Instant from, Instant to, Bucket bucket) {
		Map<LocalDateTime, long[]> byStart = new LinkedHashMap<>();
		ChronoUnit unit = bucket == Bucket.HOUR ? ChronoUnit.HOURS : ChronoUnit.DAYS;
		LocalDateTime last = LocalDateTime.ofInstant(to, ZoneOffset.UTC).truncatedTo(unit);
		for (LocalDateTime t = LocalDateTime.ofInstant(from, ZoneOffset.UTC).truncatedTo(unit); !t.isAfter(last);
				t = t.plus(1, unit)) {
			byStart.put(t, new long[2]);
		}
		for (PeriodCount c : counts) {
			long[] slot = byStart.get(c.start());
			if (slot != null) {
				slot[c.action() == AuditAction.LOGIN ? 0 : 1] += c.count();
			}
		}
		List<Activity> out = new ArrayList<>(byStart.size());
		byStart.forEach((start, n) -> out.add(new Activity(start, n[0], n[1])));
		return out;
	}

	private static List<StaffAccess> staffAccess(List<ActorCount> counts, Map<UUID, UserAccount> named) {
		Map<UUID, Map<AuditAction, Long>> byActor = new HashMap<>();
		Map<UUID, Instant> latest = new HashMap<>();
		for (ActorCount c : counts) {
			byActor.computeIfAbsent(c.actorUserId(), id -> new HashMap<>()).merge(c.action(), c.count(), Long::sum);
			latest.merge(c.actorUserId(), c.latest(), (a, b) -> a.isAfter(b) ? a : b);
		}
		return byActor.entrySet().stream().map(e -> {
			Map<AuditAction, Long> n = e.getValue();
			return new StaffAccess(AuditLogService.ref(e.getKey(), named),
					n.getOrDefault(AuditAction.KYC_DOCUMENT_VIEWED, 0L), n.getOrDefault(AuditAction.KYC_SUBMISSION_VIEWED, 0L),
					n.getOrDefault(AuditAction.BANK_ACCOUNT_PROOF_VIEWED, 0L),
					n.getOrDefault(AuditAction.TICKET_ATTACHMENT_VIEWED, 0L), n.getOrDefault(AuditAction.REPORT_GENERATED, 0L),
					n.getOrDefault(AuditAction.AUDIT_LOG_EXPORTED, 0L), latest.get(e.getKey()));
		}).sorted(Comparator.comparingLong(StaffAccess::total).reversed().thenComparing(StaffAccess::latest,
				Comparator.reverseOrder())).limit(TOP).toList();
	}

	private static Optional<UUID> uuid(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
