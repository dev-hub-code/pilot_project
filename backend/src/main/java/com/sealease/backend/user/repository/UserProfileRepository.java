package com.sealease.backend.user.repository;

import com.sealease.backend.user.entity.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID>, JpaSpecificationExecutor<UserProfile> {

	@EntityGraph(attributePaths = "user")
	@Query("select p from UserProfile p where p.user.id = :userId")
	Optional<UserProfile> findByUserId(@Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from UserProfile p join fetch p.user where p.user.id = :userId")
	Optional<UserProfile> findByUserIdForUpdate(@Param("userId") UUID userId);

}
