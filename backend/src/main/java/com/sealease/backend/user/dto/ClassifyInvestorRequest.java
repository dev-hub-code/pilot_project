package com.sealease.backend.user.dto;

import com.sealease.backend.user.entity.InvestorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClassifyInvestorRequest(@NotNull InvestorType investorType, @NotBlank @Size(max = 500) String reason) {
}
