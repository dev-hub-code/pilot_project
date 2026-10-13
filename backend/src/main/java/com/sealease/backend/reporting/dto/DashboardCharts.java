package com.sealease.backend.reporting.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.crm.entity.LeadStage;

import java.util.List;

/**
 * Trends for the admin overview, over the last twelve months including the current one. A series the
 * viewer may not see is {@code null}; months without activity are left out.
 *
 * @param payouts   what investors were paid each month, by source (FINANCE_VIEW)
 * @param invested  capital confirmed into containers each month (INVESTMENT_VIEW)
 * @param investors investor accounts registered each month (USER_VIEW)
 * @param pipeline  leads the viewer can see, per stage, in pipeline order (LEAD_VIEW)
 */
public record DashboardCharts(List<PayoutMonth> payouts, List<AmountMonth> invested, List<CountMonth> investors,
		List<StageCount> pipeline) {

	/** @param month "yyyy-MM" */
	public record PayoutMonth(String month, MoneyResponse rent, MoneyResponse capital, MoneyResponse referral) {
	}

	/** @param month "yyyy-MM" */
	public record AmountMonth(String month, MoneyResponse amount) {
	}

	/** @param month "yyyy-MM" */
	public record CountMonth(String month, long count) {
	}

	public record StageCount(LeadStage stage, long leads) {
	}

}
