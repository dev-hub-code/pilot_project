package com.sealease.backend.referral.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.referral.dto.RateVersionResponse;
import com.sealease.backend.referral.dto.ScheduleRatesRequest;
import com.sealease.backend.referral.entity.ReferralRateVersion;
import com.sealease.backend.referral.repository.ReferralRateVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Effective-dated referral rates. Rates in force are never edited: a change is a new version that
 * starts now or later, so every commission can be traced to the rates that produced it.
 */
@Service
public class ReferralRateService {

	private static final String ENTITY = "REFERRAL_RATES";
	private static final BigDecimal MAX_TOTAL = BigDecimal.valueOf(20);

	private final ReferralRateVersionRepository versions;
	private final AuditService audit;
	private final Clock clock;

	public ReferralRateService(ReferralRateVersionRepository versions, AuditService audit, Clock clock) {
		this.versions = versions;
		this.audit = audit;
		this.clock = clock;
	}

	/** The rates in force now. A version always exists: V8 seeds one. */
	@Transactional(readOnly = true)
	public ReferralRateVersion inForce() {
		return inForceAt(clock.instant());
	}

	@Transactional(readOnly = true)
	public ReferralRateVersion inForceAt(Instant at) {
		return versions.findFirstByEffectiveFromLessThanEqualAndCancelledAtIsNullOrderByEffectiveFromDesc(at)
			.orElseThrow(() -> new IllegalStateException("No referral rates in force at " + at));
	}

	@Transactional(readOnly = true)
	public List<RateVersionResponse> history() {
		UUID current = inForce().getId();
		Instant now = clock.instant();
		return versions.findAllByOrderByEffectiveFromDescCreatedAtDesc().stream()
			.map(v -> view(v, current, now))
			.toList();
	}

	@Transactional
	public RateVersionResponse schedule(UUID actorId, ScheduleRatesRequest request) {
		Instant now = clock.instant();
		// Whole seconds: an effective time is something people read and compare.
		Instant from = (request.effectiveFrom() == null ? now : request.effectiveFrom()).truncatedTo(ChronoUnit.SECONDS);
		if (from.isBefore(now.truncatedTo(ChronoUnit.SECONDS))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Rates cannot take effect in the past: commissions already paid would no longer match them");
		}
		BigDecimal total = request.percents().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(MAX_TOTAL) > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The four levels together cannot exceed 20%");
		}
		if (versions.existsByEffectiveFromAndCancelledAtIsNull(from)) {
			throw new BusinessException(ErrorCode.CONFLICT, "Other rates already take effect at that moment");
		}
		ReferralRateVersion version = versions.saveAndFlush(new ReferralRateVersion(from, request.percents(),
				request.reason().strip(), actorId, now));
		audit.record(AuditRecord.of(actorId, AuditAction.REFERRAL_RATES_SCHEDULED, ENTITY, version.getId())
			.withOldValue(Map.of("percents", plain(inForce().percents())))
			.withNewValue(Map.of("percents", plain(version.percents()), "effectiveFrom", from.toString(),
					"reason", version.getReason())));
		return view(version, inForce().getId(), now);
	}

	/** Withdraws rates that have not taken effect yet. */
	@Transactional
	public RateVersionResponse cancel(UUID actorId, UUID versionId) {
		ReferralRateVersion version = versions.findByIdForUpdate(versionId)
			.orElseThrow(() -> new ResourceNotFoundException("Referral rates", versionId));
		Instant now = clock.instant();
		if (version.isCancelled()) {
			throw new BusinessException(ErrorCode.CONFLICT, "These rates are already cancelled");
		}
		if (!version.getEffectiveFrom().isAfter(now)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Rates that have taken effect cannot be cancelled; schedule new rates instead");
		}
		version.cancel(actorId, now);
		versions.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.REFERRAL_RATES_CANCELLED, ENTITY, versionId)
			.withOldValue(Map.of("effectiveFrom", version.getEffectiveFrom().toString(),
					"percents", plain(version.percents()))));
		return view(version, inForce().getId(), now);
	}

	private static RateVersionResponse view(ReferralRateVersion v, UUID current, Instant now) {
		String state = v.isCancelled() ? "CANCELLED"
				: v.getId().equals(current) ? "IN_FORCE"
				: v.getEffectiveFrom().isAfter(now) ? "SCHEDULED" : "SUPERSEDED";
		return new RateVersionResponse(v.getId(), v.getEffectiveFrom(), v.percents(), v.getReason(), v.getCreatedBy(),
				v.getCreatedAt(), v.getCancelledAt(), state);
	}

	private static List<String> plain(List<BigDecimal> percents) {
		return percents.stream().map(p -> p.stripTrailingZeros().toPlainString()).toList();
	}

}
