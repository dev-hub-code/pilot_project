package com.sealease.backend.container.repository;

import com.sealease.backend.container.entity.ContainerDocument;
import com.sealease.backend.document.entity.DocumentPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerDocumentRepository extends JpaRepository<ContainerDocument, UUID> {

	List<ContainerDocument> findByContainerIdOrderByCreatedAtAsc(UUID containerId);

	List<ContainerDocument> findByContainerIdAndVisibleToInvestorsTrueOrderByCreatedAtAsc(UUID containerId);

	Optional<ContainerDocument> findByContainerIdAndDocumentId(UUID containerId, UUID documentId);

	boolean existsByContainerIdAndPurposeAndVisibleToInvestorsTrue(UUID containerId, DocumentPurpose purpose);

	/** First investor-visible photo per container, for marketplace cards. */
	@Query("""
			select d from ContainerDocument d
			where d.containerId in :containerIds and d.visibleToInvestors = true and d.purpose = :purpose
			order by d.createdAt asc
			""")
	List<ContainerDocument> findVisibleByContainersAndPurpose(@Param("containerIds") Collection<UUID> containerIds,
			@Param("purpose") DocumentPurpose purpose);

}
