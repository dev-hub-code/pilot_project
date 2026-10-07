package com.sealease.backend.container.dto;

import com.sealease.backend.container.entity.ContainerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContainerStatusRequest(@NotNull ContainerStatus status, @NotBlank @Size(max = 500) String reason) {
}
