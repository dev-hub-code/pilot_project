package com.sealease.backend.reporting.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every card the admin overview can show, by key. The overview renders each one only for viewers whose permissions
 * allow its data; roles (and then employees) choose among them.
 */
public final class DashboardCards {

	/** @param group the queue or kind of card it belongs to, to list them together */
	public record Card(String key, String title, String group) {
	}

	public static final List<Card> ALL = List.of(
			new Card("support-pending", "All pending tickets", "Support tickets"),
			new Card("support-mine", "Assigned to me", "Support tickets"),
			new Card("support-today", "Completed today", "Support tickets"),
			new Card("support-month", "Completed by me this month", "Support tickets"),
			new Card("support-priority", "Pending tickets by priority", "Support tickets"),
			new Card("finance-task-pending", "All pending tasks", "Finance tasks"),
			new Card("finance-task-mine", "Assigned to me", "Finance tasks"),
			new Card("finance-task-today", "Completed today", "Finance tasks"),
			new Card("finance-task-month", "Completed by me this month", "Finance tasks"),
			new Card("finance-ticket-pending", "All pending tickets", "Tickets handed to finance"),
			new Card("finance-ticket-mine", "Assigned to me", "Tickets handed to finance"),
			new Card("finance-ticket-today", "Completed today", "Tickets handed to finance"),
			new Card("finance-ticket-month", "Completed by me this month", "Tickets handed to finance"),
			new Card("queue-kyc", "KYC awaiting review", "Work queues"),
			new Card("queue-banks", "Bank accounts to verify", "Work queues"),
			new Card("queue-payments", "Payments to confirm", "Work queues"),
			new Card("queue-containers", "Paid investments awaiting a container", "Work queues"),
			new Card("kpi-investors", "Investors", "Business figures"),
			new Card("kpi-capital", "Capital invested", "Business figures"),
			new Card("kpi-payouts", "Paid to investors this month", "Business figures"),
			new Card("kpi-owed", "Owed to investors", "Business figures"),
			new Card("kpi-withdrawals", "Withdrawals in progress", "Business figures"),
			new Card("kpi-leads", "Open leads", "Business figures"),
			new Card("kpi-security", "Failed sign-ins", "Business figures"),
			new Card("chart-payouts", "Paid to investors (chart)", "Charts"),
			new Card("chart-pipeline", "Lead pipeline (chart)", "Charts"),
			new Card("chart-invested", "Capital invested (chart)", "Charts"),
			new Card("chart-investors", "New investors (chart)", "Charts"),
			new Card("roles", "Roles & permissions", "Administration"));

	public static final Set<String> KEYS = ALL.stream().map(Card::key).collect(Collectors.toUnmodifiableSet());

	private DashboardCards() {
	}

}
