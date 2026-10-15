package com.sealease.backend.withdrawal.repository;

import com.sealease.backend.ledger.service.EarningsBalance;
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

	/** The investor's withdrawals added up per currency and status: currency, status, amount. */
	@Query("select w.currency, w.status, sum(w.amount) from Withdrawal w where w.userId = :userId group by w.currency, w.status")
	List<Object[]> totalsByStatus(@Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Withdrawal w where w.id = :id")
	Optional<Withdrawal> findByIdForUpdate(@Param("id") UUID id);

	/** A batch's withdrawals, locked in id order (the same order everywhere, so no deadlocks). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Withdrawal w where w.batchId = :batchId order by w.id")
	List<Withdrawal> lockBatchItems(@Param("batchId") UUID batchId);

	List<Withdrawal> findByBatchIdOrderByCreatedAtAscIdAsc(UUID batchId);

	Optional<Withdrawal> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	/** One withdrawal may be in progress per investor, currency and earnings balance. */
	boolean existsByUserIdAndCurrencyAndBalanceAndStatusIn(UUID userId, String currency, EarningsBalance balance,
			Collection<WithdrawalStatus> statuses);

	/** Whether finance has already withdrawn this balance at the office since {@code since} (rejected ones aside). */
	boolean existsByUserIdAndBalanceAndOriginAndCreatedAtGreaterThanEqualAndStatusNot(UUID userId, EarningsBalance balance,
			com.sealease.backend.withdrawal.entity.WithdrawalOrigin origin, Instant since, WithdrawalStatus status);

	Page<Withdrawal> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	List<Withdrawal> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAt(UUID userId,
			Instant from, Instant to);

	List<Withdrawal> findByStatusIn(Collection<WithdrawalStatus> statuses);

	@Query(value = "select nextval('withdrawal_number_seq')", nativeQuery = true)
	long nextNumber();

	/** Finance tasks (withdrawals in a batch) still to pay; only {@code assigneeId}'s when it is given. */
	@Query("""
			select count(w) from Withdrawal w where w.batchId is not null
			and w.status in (com.sealease.backend.withdrawal.entity.WithdrawalStatus.BATCHED,
			                 com.sealease.backend.withdrawal.entity.WithdrawalStatus.PROCESSING)
			and (:assigneeId is null or w.taskAssigneeId = :assigneeId)""")
	long countOpenTasks(@Param("assigneeId") UUID assigneeId);

	/** Finance tasks marked paid or failed in {@code [from, to)}; only {@code assigneeId}'s when it is given. */
	@Query("""
			select count(w) from Withdrawal w where w.taskDoneAt >= :from and w.taskDoneAt < :to
			and (:assigneeId is null or w.taskAssigneeId = :assigneeId)""")
	long countTasksDone(@Param("assigneeId") UUID assigneeId, @Param("from") Instant from, @Param("to") Instant to);

}
