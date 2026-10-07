package com.sealease.backend.referral.entity;

import com.sealease.backend.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commission rates for referral levels 1-4, in force from {@code effectiveFrom} until the next
 * version starts. Rates never change once created; a version that has not started may be cancelled.
 */
@Entity
@Table(name = "referral_rate_versions")
public class ReferralRateVersion {

	public static final int LEVELS = 4;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "effective_from", nullable = false, updatable = false)
	private Instant effectiveFrom;

	@Column(name = "level1_percent", nullable = false, updatable = false, precision = 5, scale = 3)
	private BigDecimal level1Percent;

	@Column(name = "level2_percent", nullable = false, updatable = false, precision = 5, scale = 3)
	private BigDecimal level2Percent;

	@Column(name = "level3_percent", nullable = false, updatable = false, precision = 5, scale = 3)
	private BigDecimal level3Percent;

	@Column(name = "level4_percent", nullable = false, updatable = false, precision = 5, scale = 3)
	private BigDecimal level4Percent;

	@Column(name = "reason", nullable = false, updatable = false, length = 500)
	private String reason;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancelled_by")
	private UUID cancelledBy;

	protected ReferralRateVersion() {
	}

	public ReferralRateVersion(Instant effectiveFrom, List<BigDecimal> percents, String reason, UUID createdBy,
			Instant createdAt) {
		if (percents.size() != LEVELS) {
			throw new IllegalArgumentException("Exactly " + LEVELS + " referral rates are required");
		}
		this.effectiveFrom = effectiveFrom;
		this.level1Percent = percents.get(0);
		this.level2Percent = percents.get(1);
		this.level3Percent = percents.get(2);
		this.level4Percent = percents.get(3);
		this.reason = reason;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public void cancel(UUID actorId, Instant now) {
		this.cancelledAt = now;
		this.cancelledBy = actorId;
	}

	/** Rate for a level (1-4) in percent. */
	public BigDecimal percentFor(int level) {
		return switch (level) {
			case 1 -> level1Percent;
			case 2 -> level2Percent;
			case 3 -> level3Percent;
			case 4 -> level4Percent;
			default -> throw new IllegalArgumentException("Referral levels are 1 to " + LEVELS + ": " + level);
		};
	}

	public List<BigDecimal> percents() {
		return List.of(level1Percent, level2Percent, level3Percent, level4Percent);
	}

	/**
	 * The commission a level earns on a base amount: base × rate, rounded <em>down</em> to the
	 * currency's minor unit (the platform never pays out fractions of a cent).
	 */
	public Money commission(int level, Money base) {
		int digits = Math.max(base.currency().getDefaultFractionDigits(), 0);
		return Money.of(base.amount().multiply(percentFor(level)).movePointLeft(2).setScale(digits, RoundingMode.DOWN),
				base.currency());
	}

	public UUID getId() {
		return id;
	}

	public Instant getEffectiveFrom() {
		return effectiveFrom;
	}

	public String getReason() {
		return reason;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public boolean isCancelled() {
		return cancelledAt != null;
	}

}
