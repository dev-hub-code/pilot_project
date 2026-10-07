package com.sealease.backend.helpdesk.repository;

import com.sealease.backend.helpdesk.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

	List<TicketAttachment> findByTicketId(UUID ticketId);

}
