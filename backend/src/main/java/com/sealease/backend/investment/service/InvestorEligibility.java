package com.sealease.backend.investment.service;

import java.util.List;

/**
 * Whether a user may invest in an offering, and if not, why — phrased for the investor.
 */
public record InvestorEligibility(boolean eligible, List<String> reasons) {

	public InvestorEligibility {
		reasons = List.copyOf(reasons);
	}

	public static InvestorEligibility of(List<String> reasons) {
		return new InvestorEligibility(reasons.isEmpty(), reasons);
	}

}
