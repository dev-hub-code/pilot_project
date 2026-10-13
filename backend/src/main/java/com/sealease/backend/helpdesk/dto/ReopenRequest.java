package com.sealease.backend.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reason why the customer still needs help; posted to the conversation */
public record ReopenRequest(@NotBlank @Size(max = 8000) String reason) {
}
