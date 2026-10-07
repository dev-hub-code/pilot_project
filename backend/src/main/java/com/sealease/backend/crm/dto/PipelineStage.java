package com.sealease.backend.crm.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.crm.entity.LeadStage;

import java.util.List;

/**
 * Leads visible to the viewer in one stage.
 *
 * @param value estimated amounts of open leads, or invested amounts of won leads, per currency
 */
public record PipelineStage(LeadStage stage, long leads, List<MoneyResponse> value) {
}
