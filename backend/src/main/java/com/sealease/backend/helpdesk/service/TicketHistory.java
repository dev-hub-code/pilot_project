package com.sealease.backend.helpdesk.service;

import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketEvent;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.helpdesk.repository.TicketEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Appends to a ticket's (immutable) history. */
@Component
public class TicketHistory {

	private final TicketEventRepository events;

	public TicketHistory(TicketEventRepository events) {
		this.events = events;
	}

	public void record(SupportTicket ticket, UUID actorId, boolean byStaff, TicketEventType type, Object from, Object to,
			UUID subjectUserId, Instant now) {
		events.save(new TicketEvent(ticket.getId(), actorId, byStaff, type, text(from), text(to), subjectUserId, now));
	}

	private static String text(Object value) {
		if (value == null) {
			return null;
		}
		String s = value instanceof Enum<?> e ? e.name() : value.toString();
		return s.length() > 25 ? s.substring(0, 25) : s;
	}

}
