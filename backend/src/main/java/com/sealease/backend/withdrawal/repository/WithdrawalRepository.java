package com.sealease.backend.withdrawal.repository;

import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID>, JpaSpecificationExecutor<Withdrawal> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Withdrawal w where w.id = :id")
	Optional<Withdrawal> findByIdForUpdate(@Param("id") UUID id);

	/** Approved withdrawals of one currency, oldest first, locked for batching. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Withdrawal w where w.status = :status and w.currency = :currency order by w.createdAt, w.id")
	List<Withdrawal> findForBatching(@Param("status") WithdrawalStatus status, @Param("currency") String currency,
			Pageable pageable);

	/** A batch's withdrawals, locked in id order (the same order everywhere, so no deadlocks). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Withdrawal w where w.batchId = :batchId order by w.id")
	List<Withdrawal> lockBatchItems(@Param("batchId") UUID batchId);

	List<Withdrawal> findByBatchIdOrderByCreatedAtAscIdAsc(UUID batchId);

	Optional<Withdrawal> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	boolean existsByUserIdAndCurrencyAndStatusIn(UUID userId, String currency, Collection<WithdrawalStatus> statuses);

	Page<Withdrawal> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	List<Withdrawal> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAt(UUID userId,
			Instant from, Instant to);

	List<Withdrawal> findByStatusIn(Collection<WithdrawalStatus> statuses);

	@Query(value = "select nextval('withdrawal_number_seq')", nativeQuery = true)
	long nextNumber();

}
