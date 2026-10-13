package com.sealease.backend.auth.repository;

import com.sealease.backend.auth.entity.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

	/** The account's current code; earlier ones are retired whenever a new one is sent. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetCode> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

	@Modifying
	@Query("update PasswordResetCode c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
	int retireUnused(@Param("userId") UUID userId, @Param("now") Instant now);

}
