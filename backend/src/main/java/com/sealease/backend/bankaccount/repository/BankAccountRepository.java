package com.sealease.backend.bankaccount.repository;

import com.sealease.backend.bankaccount.entity.BankAccount;
import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

	List<BankAccount> findByUserIdAndStatusNotOrderByCreatedAtAsc(UUID userId, BankAccountStatus excluded);

	List<BankAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

	long countByUserIdAndStatusNot(UUID userId, BankAccountStatus excluded);

	boolean existsByUserIdAndAccountFingerprintAndStatusNot(UUID userId, String fingerprint, BankAccountStatus excluded);

	Page<BankAccount> findByStatus(BankAccountStatus status, Pageable pageable);

	/** Other users registering the same account is a fraud signal shown to reviewers. */
	@Query("""
			select count(distinct b.userId) from BankAccount b
			where b.accountFingerprint = :fingerprint and b.userId <> :userId
			""")
	long countOtherUsersWithFingerprint(@Param("fingerprint") String fingerprint, @Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BankAccount b where b.id = :id")
	Optional<BankAccount> findByIdForUpdate(@Param("id") UUID id);

	/** Serialises account changes per user (primary flag, account limit). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BankAccount b where b.userId = :userId and b.status <> com.sealease.backend.bankaccount.entity.BankAccountStatus.REMOVED")
	List<BankAccount> lockActiveForUser(@Param("userId") UUID userId);

}
