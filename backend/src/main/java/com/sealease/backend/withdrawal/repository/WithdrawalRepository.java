package com.sealease.backend.withdrawal.repository;

import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

	Optional<Withdrawal> findByReference(String reference);

	/**
	 * A withdrawal's facts a payout webhook checks, read without loading the withdrawal: it is then locked
	 * ({@link #findByIdForUpdate}) and so read as it is under the lock, never from a copy read earlier in the transaction.
	 */
	interface PayoutKeys {

		UUID getId();

		BigDecimal getNetAmount();

		String getCurrency();

	}

	@Query("select w.id as id, w.netAmount as netAmount, w.currency as currency from Withdrawal w where w.reference = :reference")
	Optional<PayoutKeys> findPayoutKeysByReference(@Param("reference") String reference);

	/** The batch a withdrawal is in (empty when none, or no such withdrawal), without loading it. */
	@Query("select w.batchId from Withdrawal w where w.id = :id")
	Optional<UUID> findBatchIdById(@Param("id") UUID id);

	@Query("select w.status from Withdrawal w where w.id = :id")
	Optional<WithdrawalStatus> findStatusById(@Param("id") UUID id);

	/**
	 * Paid withdrawals other than {@code excluded} recorded with this bank reference or cash receipt (any case). With
	 * {@code otherInvestorsOnly}, only those of investors other than {@code userId}: one cash receipt may cover several
	 * payouts an investor collects at once.
	 */
	@Query("""
			select w.reference from Withdrawal w
			where w.status = com.sealease.backend.withdrawal.entity.WithdrawalStatus.PAID and w.payoutMethod = :method
			  and upper(w.payoutReference) = upper(:payoutReference) and w.id <> :excluded
			  and (:otherInvestorsOnly = false or w.userId <> :userId)""")
	List<String> findPaidWithPayoutReference(@Param("method") WithdrawalPayoutMethod method,
			@Param("payoutReference") String payoutReference, @Param("excluded") UUID excluded,
			@Param("otherInvestorsOnly") boolean otherInvestorsOnly, @Param("userId") UUID userId);

	List<Withdrawal> findByUserIdAndStatusIn(UUID userId, Collection<WithdrawalStatus> statuses);

	List<Withdrawal> findByStatusIn(Collection<WithdrawalStatus> statuses);

	@Query(value = "select nextval('withdrawal_number_seq')", nativeQuery = true)
	long nextNumber();

}
