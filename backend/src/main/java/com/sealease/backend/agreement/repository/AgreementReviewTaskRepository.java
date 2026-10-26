package com.sealease.backend.agreement.repository;

import com.sealease.backend.agreement.entity.AgreementReviewTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AgreementReviewTaskRepository extends JpaRepository<AgreementReviewTask, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from AgreementReviewTask t where t.id = :id")
	Optional<AgreementReviewTask> findByIdForUpdate(@Param("id") UUID id);

	/** Tasks in a status, optionally only those assigned to {@code assignee}; oldest first while open. */
	@Query("""
			select t from AgreementReviewTask t
			where t.status = :status and (:assignee is null or t.assignedTo = :assignee)
			""")
	Page<AgreementReviewTask> search(@Param("status") AgreementReviewTask.Status status,
			@Param("assignee") UUID assignee, Pageable pageable);

	long countByStatus(AgreementReviewTask.Status status);

}
