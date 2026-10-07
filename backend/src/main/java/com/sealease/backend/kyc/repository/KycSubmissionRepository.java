package com.sealease.backend.kyc.repository;

import com.sealease.backend.kyc.entity.KycSubmission;
import com.sealease.backend.kyc.entity.KycSubmissionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, UUID> {

	Optional<KycSubmission> findFirstByUserIdOrderBySubmittedAtDesc(UUID userId);

	List<KycSubmission> findByUserIdOrderBySubmittedAtDesc(UUID userId);

	Page<KycSubmission> findByStatus(KycSubmissionStatus status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from KycSubmission s where s.id = :id")
	Optional<KycSubmission> findByIdForUpdate(@Param("id") UUID id);

}
