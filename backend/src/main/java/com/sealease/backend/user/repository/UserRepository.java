package com.sealease.backend.user.repository;

import com.sealease.backend.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	/** Ids of users whose email or name contains {@code like} (lower case, LIKE-escaped with '\\'). */
	@Query("""
			select u.id from User u
			where lower(u.email) like :like escape '\\'
			   or lower(u.firstName) like :like escape '\\'
			   or lower(u.lastName) like :like escape '\\'
			   or lower(concat(u.firstName, ' ', u.lastName)) like :like escape '\\'
			""")
	List<UUID> findIdsMatching(@Param("like") String like, Pageable limit);

	@Query("select u.id from User u where u.userNumber = :number")
	Optional<UUID> findIdByUserNumber(@Param("number") long number);

	boolean existsByEmail(String email);

	/** Accounts locked after repeated failed sign-ins whose lock has not yet run out, latest lock first. */
	List<User> findByLockedUntilAfterOrderByLockedUntilDesc(Instant now);

	/**
	 * Row lock for read-modify-write of login counters, so concurrent failed attempts cannot
	 * under-count (a lost update would let an attacker exceed the lockout threshold).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") UUID id);

}
