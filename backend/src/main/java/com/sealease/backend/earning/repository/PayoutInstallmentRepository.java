package com.sealease.backend.earning.repository;

import com.sealease.backend.earning.entity.PayoutInstallment;
import com.sealease.backend.earning.entity.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutInstallmentRepository
		extends JpaRepository<PayoutInstallment, UUID>, JpaSpecificationExecutor<PayoutInstallment> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PayoutInstallment p where p.id = :id")
	Optional<PayoutInstallment> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select p.id from PayoutInstallment p
			where p.status = com.sealease.backend.earning.entity.PayoutStatus.SCHEDULED and p.dueOn <= :day
			order by p.dueOn, p.id
			""")
	List<UUID> findDueIds(@Param("day") LocalDate day, Pageable limit);

	boolean existsByHoldingIdAndStatus(UUID holdingId, PayoutStatus status);

	List<PayoutInstallment> findByUserIdOrderByDueOnAscInstallmentNumberAsc(UUID userId);

	List<PayoutInstallment> findByHoldingIdOrderByInstallmentNumber(UUID holdingId);

	/** Scheduled payouts due on or before the day, per currency: count and total. */
	@Query("""
			select p.currency, count(p), sum(p.rentAmount + p.capitalAmount) from PayoutInstallment p
			where p.status = com.sealease.backend.earning.entity.PayoutStatus.SCHEDULED and p.dueOn <= :day
			group by p.currency
			""")
	List<Object[]> dueTotals(@Param("day") LocalDate day);

}
