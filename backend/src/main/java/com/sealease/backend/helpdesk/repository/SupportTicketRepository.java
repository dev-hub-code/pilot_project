package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.SupportTeam;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from SupportTicket t where t.id = :id")
	Optional<SupportTicket> findByIdForUpdate(@Param("id") UUID id);

	Page<SupportTicket> findByUserIdOrderByLastMessageAtDesc(UUID userId, Pageable pageable);

	/** Resolved tickets resolved at or before {@code cutoff}: due to close by themselves. */
	@Query("""
			select t.id from SupportTicket t
			where t.status = com.sealease.backend.helpdesk.entity.TicketStatus.RESOLVED and t.resolvedAt <= :cutoff
			order by t.resolvedAt
			""")
	List<UUID> findResolvedBefore(@Param("cutoff") Instant cutoff);

	@Query(value = "select nextval('ticket_number_seq')", nativeQuery = true)
	long nextNumber();

	/** A team's tickets not yet resolved or closed; only {@code assigneeId}'s when it is given. */
	@Query("""
			select count(t) from SupportTicket t where t.team = :team
			and t.status in (com.sealease.backend.helpdesk.entity.TicketStatus.OPEN,
			                 com.sealease.backend.helpdesk.entity.TicketStatus.WAITING_ON_CUSTOMER)
			and (:assigneeId is null or t.assigneeId = :assigneeId)""")
	long countPendingForTeam(@Param("team") SupportTeam team, @Param("assigneeId") UUID assigneeId);

	/** A team's pending tickets past their first-response target without a staff reply. */
	@Query("""
			select count(t) from SupportTicket t where t.team = :team
			and t.status in (com.sealease.backend.helpdesk.entity.TicketStatus.OPEN,
			                 com.sealease.backend.helpdesk.entity.TicketStatus.WAITING_ON_CUSTOMER)
			and t.firstRespondedAt is null and t.firstResponseDueAt < :now""")
	long countOverdueForTeam(@Param("team") SupportTeam team, @Param("now") Instant now);

	/**
	 * A team's pending tickets by priority: [priority, pending, overdue, unassigned and waiting for a reply,
	 * assigned to {@code viewerId}]. Priorities without pending tickets are left out.
	 */
	@Query("""
			select t.priority, count(t),
			  sum(case when t.firstRespondedAt is null and t.firstResponseDueAt < :now then 1 else 0 end),
			  sum(case when t.assigneeId is null
			           and t.status = com.sealease.backend.helpdesk.entity.TicketStatus.OPEN then 1 else 0 end),
			  sum(case when t.assigneeId = :viewerId then 1 else 0 end)
			from SupportTicket t where t.team = :team
			and t.status in (com.sealease.backend.helpdesk.entity.TicketStatus.OPEN,
			                 com.sealease.backend.helpdesk.entity.TicketStatus.WAITING_ON_CUSTOMER)
			group by t.priority""")
	List<Object[]> pendingByPriority(@Param("team") SupportTeam team, @Param("viewerId") UUID viewerId,
			@Param("now") Instant now);

	/** A team's tickets resolved (or closed without resolving) in {@code [from, to)}; only {@code assigneeId}'s when given. */
	@Query("""
			select count(t) from SupportTicket t where t.team = :team
			and t.status in (com.sealease.backend.helpdesk.entity.TicketStatus.RESOLVED,
			                 com.sealease.backend.helpdesk.entity.TicketStatus.CLOSED)
			and coalesce(t.resolvedAt, t.closedAt) >= :from and coalesce(t.resolvedAt, t.closedAt) < :to
			and (:assigneeId is null or t.assigneeId = :assigneeId)""")
	long countDoneForTeam(@Param("team") SupportTeam team, @Param("assigneeId") UUID assigneeId,
			@Param("from") Instant from, @Param("to") Instant to);

}
