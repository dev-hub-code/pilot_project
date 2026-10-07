package com.sealease.backend.crm.dto;

import com.sealease.backend.crm.entity.LeadSource;
import com.sealease.backend.crm.entity.LeadStage;

/**
 * @param owner {@code me}, {@code unassigned}, a user id (managers only) or {@code null} for everything visible
 * @param due   only open leads whose follow-up date has passed
 */
public record LeadSearchCriteria(String q, LeadStage stage, LeadSource source, String owner, boolean due) {
}
