package com.sealease.backend.common.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for decisions that must be justified (rejections, suspensions); the reason is audited. */
public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
