package com.sealease.backend.investment.repository;

import com.sealease.backend.investment.entity.Holding;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.sealease.backend.investment.entity.HoldingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from Holding h where h.id = :id")
	Optional<Holding> findByIdForUpdate(@Param("id") UUID id);

	List<Holding> findByUserIdOrderByConfirmedAtDesc(UUID userId);

	/** Paid investments still waiting for finance to assign a container, oldest first. */
	Page<Holding> findByStatusOrderByConfirmedAtAscIdAsc(HoldingStatus status, Pageable pageable);

	long countByStatus(HoldingStatus status);

	List<Holding> findByOrderIdOrderByCreatedAt(UUID orderId);

	List<Holding> findByOrderItemIdIn(Collection<UUID> orderItemIds);

	@Query("select h.productId, count(distinct h.userId) from Holding h where h.productId in :productIds group by h.productId")
	List<Object[]> countInvestorsByProduct(@Param("productIds") Collection<UUID> productIds);

	@Query("select h.productId, count(h) from Holding h where h.productId in :productIds group by h.productId")
	List<Object[]> countContainersByProduct(@Param("productIds") Collection<UUID> productIds);

	@Query("select h.currency, sum(h.amount) from Holding h group by h.currency")
	List<Object[]> sumAmountByCurrency();

	/** Rows of ("yyyy-MM", sum of amount) for holdings confirmed in one currency since {@code from}, by UTC month. */
	@Query(value = """
			select to_char(confirmed_at at time zone 'UTC', 'YYYY-MM'), sum(amount) from holdings
			where currency = :currency and confirmed_at >= :from group by 1 order by 1""", nativeQuery = true)
	List<Object[]> sumAmountByMonth(@Param("currency") String currency, @Param("from") Instant from);

}
