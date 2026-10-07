package com.sealease.backend.crm.dto;

import com.sealease.backend.crm.entity.LeadStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param reason required when the lead is lost */
public record StageChangeRequest(@NotNull LeadStage stage, @Size(max = 500) String reason) {
}
