package com.sealease.backend.earning.repository;

import com.sealease.backend.earning.entity.Earning;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EarningRepository extends JpaRepository<Earning, UUID> {

	Page<Earning> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

	List<Earning> findByReceiptIdOrderByOwnershipPercentDescIdAsc(UUID receiptId);

	/** What each of an investor's holdings has earned so far. */
	@Query("""
			select new com.sealease.backend.earning.repository.HoldingEarningsTotal(
			    e.holdingId, e.productId, e.currency, sum(e.netAmount), count(e), max(e.createdAt))
			from Earning e where e.userId = :userId
			group by e.holdingId, e.productId, e.currency
			""")
	List<HoldingEarningsTotal> totalsByHolding(@Param("userId") UUID userId);

}
