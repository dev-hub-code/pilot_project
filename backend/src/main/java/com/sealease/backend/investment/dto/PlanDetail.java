package com.sealease.backend.investment.dto;

import com.sealease.backend.investment.service.InvestorEligibility;

/** A plan and whether the viewer may invest in it, with the reasons if not. */
public record PlanDetail(PlanView plan, InvestorEligibility eligibility) {
}
