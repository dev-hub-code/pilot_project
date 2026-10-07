package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

	List<Holding> findByUserIdOrderByConfirmedAtDesc(UUID userId);

	List<Holding> findByOrderIdOrderByCreatedAt(UUID orderId);

	List<Holding> findByProductIdAndStatusOrderById(UUID productId, HoldingStatus status);

	@Query("select h.productId, count(distinct h.userId) from Holding h where h.productId in :productIds group by h.productId")
	List<Object[]> countInvestorsByProduct(@Param("productIds") Collection<UUID> productIds);

}
