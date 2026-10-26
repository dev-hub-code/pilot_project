package com.sealease.backend.earning.repository;

import com.sealease.backend.earning.entity.RentAccrual;
import jakarta.persistence.LockModeType;
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

public interface RentAccrualRepository extends JpaRepository<RentAccrual, UUID>, JpaSpecificationExecutor<RentAccrual> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from RentAccrual a where a.id = :id")
	Optional<RentAccrual> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Leases with at least one whole hour (ended by {@code hour}) still to credit, of active accounts: a suspended or
	 * disabled investor's rent is held back, and credited from where it stopped once the account is active again.
	 */
	@Query("""
			select a.id from RentAccrual a
			where a.completedAt is null and a.creditedUntil < :hour
			  and exists (select u.id from User u where u.id = a.userId
			              and u.status = com.sealease.backend.user.entity.UserStatus.ACTIVE)
			order by a.creditedUntil, a.id
			""")
	List<UUID> findUncreditedIds(@Param("hour") Instant hour, Pageable limit);

	/** Leases of active accounts with hours ended by {@code hour} and not credited yet (held-back rent is not due). */
	@Query("""
			select a from RentAccrual a
			where a.completedAt is null and a.creditedUntil < :hour
			  and exists (select u.id from User u where u.id = a.userId
			              and u.status = com.sealease.backend.user.entity.UserStatus.ACTIVE)
			""")
	List<RentAccrual> findBehind(@Param("hour") Instant hour);

	List<RentAccrual> findByUserIdOrderByStartsAtAsc(UUID userId);

	/** The investor's leases that run in part from {@code from} to {@code to}. */
	@Query("""
			select a from RentAccrual a
			where a.userId = :userId and a.startsAt < :to and a.endsAt > :from
			""")
	List<RentAccrual> findOverlapping(@Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

	Optional<RentAccrual> findByHoldingId(UUID holdingId);

	List<RentAccrual> findByHoldingIdIn(Collection<UUID> holdingIds);

}
