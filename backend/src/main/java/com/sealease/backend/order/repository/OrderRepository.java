package com.sealease.backend.order.repository;

import com.sealease.backend.order.entity.InvestmentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<InvestmentOrder, UUID>, JpaSpecificationExecutor<InvestmentOrder> {

	/** Serialises payment settlement, cancellation and expiry of one order. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from InvestmentOrder o where o.id = :id")
	Optional<InvestmentOrder> findByIdForUpdate(@Param("id") UUID id);

	@Query("select o from InvestmentOrder o where o.userId = :userId and o.idempotencyKey = :key")
	Optional<InvestmentOrder> findByIdempotencyKey(@Param("userId") UUID userId, @Param("key") String key);

	Page<InvestmentOrder> findByUserId(UUID userId, Pageable pageable);

	@Query("""
			select o.id from InvestmentOrder o
			where o.status = com.sealease.backend.order.entity.OrderStatus.PENDING_PAYMENT and o.expiresAt <= :now
			order by o.expiresAt
			""")
	List<UUID> findDueForExpiry(@Param("now") Instant now, Pageable limit);

	@Query(value = "select nextval('order_number_seq')", nativeQuery = true)
	long nextNumber();

}
