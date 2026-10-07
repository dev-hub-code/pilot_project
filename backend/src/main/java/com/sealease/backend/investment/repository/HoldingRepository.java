package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from Holding h where h.id = :id")
	Optional<Holding> findByIdForUpdate(@Param("id") UUID id);

	List<Holding> findByUserIdOrderByConfirmedAtDesc(UUID userId);

	List<Holding> findByOrderIdOrderByCreatedAt(UUID orderId);

	@Query("select h.productId, count(distinct h.userId) from Holding h where h.productId in :productIds group by h.productId")
	List<Object[]> countInvestorsByProduct(@Param("productIds") Collection<UUID> productIds);

	@Query("select h.productId, count(h) from Holding h where h.productId in :productIds group by h.productId")
	List<Object[]> countContainersByProduct(@Param("productIds") Collection<UUID> productIds);

	@Query("select h.currency, sum(h.amount) from Holding h group by h.currency")
	List<Object[]> sumAmountByCurrency();

}
