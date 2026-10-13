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

	/** Installments with at least one whole day (before {@code today}) still to credit. */
	@Query("""
			select p.id from PayoutInstallment p
			where p.status = com.sealease.backend.earning.entity.PayoutStatus.SCHEDULED and p.creditedThrough < :today
			order by p.creditedThrough, p.id
			""")
	List<UUID> findUncreditedIds(@Param("today") LocalDate today, Pageable limit);

	boolean existsByHoldingIdAndStatus(UUID holdingId, PayoutStatus status);

	List<PayoutInstallment> findByUserIdOrderByDueOnAscInstallmentNumberAsc(UUID userId);

	List<PayoutInstallment> findByHoldingIdOrderByInstallmentNumber(UUID holdingId);

	List<PayoutInstallment> findByUserIdAndDueOnBetweenOrderByDueOn(UUID userId, LocalDate from, LocalDate to);

	/** Installments with whole days still to credit (before {@code today}), per currency: count and amount. */
	@Query(value = """
			select p.currency, count(*),
			       round(sum((p.rent_amount + p.capital_amount) * (least(p.period_end, :today) - p.period_start)
			           / (p.period_end - p.period_start) - p.credited_rent - p.credited_capital), 2)
			from payout_installments p
			where p.status = 'SCHEDULED' and p.credited_through < :today
			group by p.currency
			""", nativeQuery = true)
	List<Object[]> dueTotals(@Param("today") LocalDate today);

}
