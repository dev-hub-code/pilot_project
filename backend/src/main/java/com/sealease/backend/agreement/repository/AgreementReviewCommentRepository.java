package com.sealease.backend.agreement.repository;

import com.sealease.backend.agreement.entity.AgreementReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AgreementReviewCommentRepository extends JpaRepository<AgreementReviewComment, UUID> {

	List<AgreementReviewComment> findByTaskIdOrderByCreatedAt(UUID taskId);

	/** Comments per task, as [taskId, count]; tasks without comments are left out. */
	@Query("select c.taskId, count(c) from AgreementReviewComment c where c.taskId in :taskIds group by c.taskId")
	List<Object[]> countByTask(@Param("taskIds") Collection<UUID> taskIds);

}
