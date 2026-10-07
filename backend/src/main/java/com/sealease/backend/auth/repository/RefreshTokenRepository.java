package com.sealease.backend.auth.repository;

import com.sealease.backend.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/** Locks the row so that two concurrent refreshes with the same token are serialised. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t where t.tokenHash = :hash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);

}
