package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketEventRepository extends JpaRepository<TicketEvent, UUID> {

	List<TicketEvent> findByTicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);

}
