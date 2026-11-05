package com.sealease.backend.helpdesk.entity;

/** What the ticket is about (top level); configurable subcategories sit below each (support_categories). */
public enum TicketCategory {
	ACCOUNT,
	INVESTMENT,
	PAYMENT,
	EARNINGS,
	WITHDRAWAL,
	KYC,
	REFERRAL,
	TECHNICAL,
	OTHER
}
