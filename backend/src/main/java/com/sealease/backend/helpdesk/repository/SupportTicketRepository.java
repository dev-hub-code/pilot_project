package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from SupportTicket t where t.id = :id")
	Optional<SupportTicket> findByIdForUpdate(@Param("id") UUID id);

	Page<SupportTicket> findByUserIdOrderByLastMessageAtDesc(UUID userId, Pageable pageable);

	long countByStatus(TicketStatus status);

	@Query("""
			select count(t) from SupportTicket t
			where t.firstRespondedAt is null and t.firstResponseDueAt < :now
			  and t.status <> com.sealease.backend.helpdesk.entity.TicketStatus.CLOSED
			""")
	long countOverdue(@Param("now") Instant now);

	@Query(value = "select nextval('ticket_number_seq')", nativeQuery = true)
	long nextNumber();

}
