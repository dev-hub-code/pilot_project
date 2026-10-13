package com.sealease.backend.auth.repository;

import com.sealease.backend.auth.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

	/** Locks the row so that a link opened twice at once is used once. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from EmailVerificationToken t where t.tokenHash = :hash")
	Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);

	/** The user's current (unused) link and code; earlier ones are retired whenever a new email is sent. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerificationToken> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

	/** Retires the user's earlier links when a new one is sent, so only the latest email works. */
	@Modifying
	@Query("update EmailVerificationToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
	int retireUnused(@Param("userId") UUID userId, @Param("now") Instant now);

}
