package com.sealease.backend.order.dto;

import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;

import java.time.Instant;
import java.util.UUID;

/** Paid investments waiting for finance to assign a container. */
public record ContainerAssignmentQueue(PageResponse<Pending> pending) {

	/** One paid investment (one container) awaiting assignment; {@code assignee} is who took the task, if anyone. */
	public record Pending(UUID holdingId, UUID orderId, String orderNumber, UUID investorId, String investorName,
			String investorEmail, String productCode, String productTitle, ContainerType containerType,
			MoneyResponse amount, int tenureMonths, Instant paidAt, Assignee assignee) {
	}

}
