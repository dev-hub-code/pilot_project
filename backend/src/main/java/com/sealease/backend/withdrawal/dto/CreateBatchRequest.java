package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.validation.IsoCurrency;
import jakarta.validation.constraints.NotBlank;

public record CreateBatchRequest(@NotBlank @IsoCurrency String currency) {
}
