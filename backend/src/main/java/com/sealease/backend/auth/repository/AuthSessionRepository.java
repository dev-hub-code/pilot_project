package com.sealease.backend.auth.repository;

import com.sealease.backend.auth.entity.AuthSession;
import com.sealease.backend.auth.entity.SessionRevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	@Query("""
			select count(s) > 0 from AuthSession s
			where s.id = :id and s.revokedAt is null and s.expiresAt > :now
			""")
	boolean isActive(@Param("id") UUID id, @Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from AuthSession s where s.id = :id")
	Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update AuthSession s
			set s.revokedAt = :now, s.revokedReason = :reason, s.version = s.version + 1
			where s.userId in :userIds and s.revokedAt is null
			""")
	int revokeAllForUsers(@Param("userIds") Collection<UUID> userIds,
			@Param("reason") SessionRevocationReason reason, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update AuthSession s
			set s.revokedAt = :now, s.revokedReason = :reason, s.version = s.version + 1
			where s.userId = :userId and s.id <> :keep and s.revokedAt is null
			""")
	int revokeOthersForUser(@Param("userId") UUID userId, @Param("keep") UUID keepSessionId,
			@Param("reason") SessionRevocationReason reason, @Param("now") Instant now);

}
