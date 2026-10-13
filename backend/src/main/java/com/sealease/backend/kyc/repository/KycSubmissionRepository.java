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

	/** Whether another user has a submission under review or approved with this Aadhaar number. */
	@Query("""
			select count(s) > 0 from KycSubmission s where s.aadhaarFingerprint = :fingerprint and s.userId <> :userId
			and s.status in (com.sealease.backend.kyc.entity.KycSubmissionStatus.PENDING,
			                 com.sealease.backend.kyc.entity.KycSubmissionStatus.APPROVED)""")
	boolean aadhaarHeldByAnotherUser(@Param("fingerprint") String fingerprint, @Param("userId") UUID userId);

	/** Whether another user has a submission under review or approved with this PAN. */
	@Query("""
			select count(s) > 0 from KycSubmission s where s.panFingerprint = :fingerprint and s.userId <> :userId
			and s.status in (com.sealease.backend.kyc.entity.KycSubmissionStatus.PENDING,
			                 com.sealease.backend.kyc.entity.KycSubmissionStatus.APPROVED)""")
	boolean panHeldByAnotherUser(@Param("fingerprint") String fingerprint, @Param("userId") UUID userId);

	@Query("select s from KycSubmission s where s.aadhaarNumberEncrypted is not null and s.aadhaarFingerprint is null")
	List<KycSubmission> findUnfingerprinted();

	/** The investor's draft (one of Aadhaar and PAN uploaded, the other still to come), locked. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from KycSubmission s where s.userId = :userId and s.status = com.sealease.backend.kyc.entity.KycSubmissionStatus.DRAFT")
	Optional<KycSubmission> findDraftForUpdate(@Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from KycSubmission s where s.id = :id")
	Optional<KycSubmission> findByIdForUpdate(@Param("id") UUID id);

}
