package com.sealease.backend.reporting.catalog;

/** The groups reports are listed under, in this order. */
public enum ReportCategory {
	FINANCIAL("Financial reports"),
	INVESTMENT("Investment reports"),
	INVESTOR("Investor reports"),
	EARNINGS("Earnings reports"),
	PAYMENT("Payment reports"),
	WITHDRAWAL("Withdrawal reports"),
	REFERRAL("Referral reports"),
	CONTAINER("Container reports"),
	OPERATIONAL("Operational reports"),
	CRM("CRM reports"),
	HELPDESK("Helpdesk reports"),
	COMPLIANCE("Compliance reports");

	private final String label;

	ReportCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
