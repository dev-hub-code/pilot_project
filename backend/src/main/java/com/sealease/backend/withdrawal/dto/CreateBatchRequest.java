package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.validation.PlatformCurrency;
import jakarta.validation.constraints.NotBlank;

public record CreateBatchRequest(@NotBlank @PlatformCurrency String currency) {
}
