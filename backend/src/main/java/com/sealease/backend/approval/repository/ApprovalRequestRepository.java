package com.sealease.backend.approval.repository;

import com.sealease.backend.approval.entity.ApprovalRequest;
import com.sealease.backend.approval.entity.ApprovalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

	Page<ApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

	/** Taken before deciding, so two people deciding at once are served one after the other. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from ApprovalRequest a where a.id = :id")
	Optional<ApprovalRequest> findForDecision(@Param("id") UUID id);

}
