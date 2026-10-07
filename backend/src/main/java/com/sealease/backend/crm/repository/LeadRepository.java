package com.sealease.backend.crm.repository;

import com.sealease.backend.crm.entity.Lead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from Lead l where l.id = :id")
	Optional<Lead> findByIdForUpdate(@Param("id") UUID id);

	/** The open lead for an email (there is at most one). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select l from Lead l where l.email = :email
			  and l.stage not in (com.sealease.backend.crm.entity.LeadStage.WON, com.sealease.backend.crm.entity.LeadStage.LOST)
			""")
	Optional<Lead> lockOpenByEmail(@Param("email") String email);

	/** Open leads linked to an account, oldest first. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select l from Lead l where l.userId = :userId
			  and l.stage not in (com.sealease.backend.crm.entity.LeadStage.WON, com.sealease.backend.crm.entity.LeadStage.LOST)
			order by l.createdAt
			""")
	List<Lead> lockOpenByUserId(@Param("userId") UUID userId);

	@Query(value = "select nextval('lead_number_seq')", nativeQuery = true)
	long nextNumber();

}
