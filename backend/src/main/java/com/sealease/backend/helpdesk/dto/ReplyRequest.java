package com.sealease.backend.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param internal staff only: a note other staff can see and the customer cannot */
public record ReplyRequest(@NotBlank @Size(max = 8000) String body, boolean internal) {
}
