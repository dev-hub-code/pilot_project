package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.PlanPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanPhotoRepository extends JpaRepository<PlanPhoto, UUID> {

	List<PlanPhoto> findByProductIdOrderByCreatedAtAscIdAsc(UUID productId);

	List<PlanPhoto> findByProductIdInOrderByCreatedAtAscIdAsc(Collection<UUID> productIds);

	Optional<PlanPhoto> findByProductIdAndDocumentId(UUID productId, UUID documentId);

	long countByProductId(UUID productId);

}
