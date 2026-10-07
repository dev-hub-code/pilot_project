package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.CapacityMovement;
import com.sealease.backend.investment.entity.CapacityMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapacityMovementRepository extends JpaRepository<CapacityMovement, UUID> {

	Optional<CapacityMovement> findByProductIdAndMovementTypeAndReference(UUID productId, CapacityMovementType type,
			String reference);

	List<CapacityMovement> findByProductIdAndReference(UUID productId, String reference);

	Page<CapacityMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

	/** An investor's exposure to an offering: reservations not released (committed or outstanding). */
	@Query("""
			select coalesce(sum(case
			    when m.movementType = com.sealease.backend.investment.entity.CapacityMovementType.RESERVE then m.amount
			    when m.movementType = com.sealease.backend.investment.entity.CapacityMovementType.RELEASE then -m.amount
			    else 0 end), 0)
			from CapacityMovement m where m.productId = :productId and m.investorUserId = :investorId
			""")
	BigDecimal exposureOf(@Param("productId") UUID productId, @Param("investorId") UUID investorId);

}
