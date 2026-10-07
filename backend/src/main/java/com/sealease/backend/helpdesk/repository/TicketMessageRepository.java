package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

	List<TicketMessage> findByTicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);

}
