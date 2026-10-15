package com.sealease.backend.reporting.dto;

import com.sealease.backend.helpdesk.entity.TicketPriority;

import java.util.List;

/**
 * The viewer's queues for the overview: finance tasks (withdrawals to pay), tickets handed to finance, and support
 * tickets. A queue the viewer does not work on is null. Days and months are in the business's time zone.
 */
public record Workload(Counts financeTasks, Counts financeTickets, Counts supportTickets,
		List<PriorityRow> supportByPriority) {

	/**
	 * Pending support tickets of one priority.
	 *
	 * @param unassigned   waiting for a reply with nobody on them
	 * @param assignedToMe the viewer's
	 */
	public record PriorityRow(TicketPriority priority, long pending, long overdue, long unassigned, long assignedToMe) {
	}

	/**
	 * @param pending           not done yet, by anyone
	 * @param overdue           of those, past their first-response target without a reply; null for finance tasks
	 * @param assignedToMe      of those, the viewer's
	 * @param doneToday         done today by anyone; {@code doneTodayByMe} of them by the viewer
	 * @param doneThisMonth     done this month by anyone; {@code doneThisMonthByMe} of them by the viewer
	 */
	public record Counts(long pending, Long overdue, long assignedToMe, long doneToday, long doneTodayByMe,
			long doneThisMonth, long doneThisMonthByMe) {
	}

}
