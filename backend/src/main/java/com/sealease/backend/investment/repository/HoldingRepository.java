package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

	List<Holding> findByUserIdOrderByConfirmedAtDesc(UUID userId);

	List<Holding> findByOrderIdOrderByCreatedAt(UUID orderId);

	List<Holding> findByProductIdAndStatusOrderById(UUID productId, HoldingStatus status);

}
