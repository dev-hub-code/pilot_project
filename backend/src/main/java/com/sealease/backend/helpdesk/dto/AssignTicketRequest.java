package com.sealease.backend.helpdesk.dto;

import java.util.UUID;

/** @param assigneeId {@code null} returns the ticket to the shared queue */
public record AssignTicketRequest(UUID assigneeId) {
}
