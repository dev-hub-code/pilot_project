package com.sealease.backend.referral.repository;

import com.sealease.backend.referral.entity.ReferralRateVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRateVersionRepository extends JpaRepository<ReferralRateVersion, UUID> {

	/** The version in force at {@code at}: the latest non-cancelled one that has started. */
	Optional<ReferralRateVersion> findFirstByEffectiveFromLessThanEqualAndCancelledAtIsNullOrderByEffectiveFromDesc(
			Instant at);

	List<ReferralRateVersion> findAllByOrderByEffectiveFromDescCreatedAtDesc();

	boolean existsByEffectiveFromAndCancelledAtIsNull(Instant effectiveFrom);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from ReferralRateVersion v where v.id = :id")
	Optional<ReferralRateVersion> findByIdForUpdate(@Param("id") UUID id);

}
