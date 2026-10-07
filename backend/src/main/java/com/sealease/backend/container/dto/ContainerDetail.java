package com.sealease.backend.container.dto;

import com.sealease.backend.container.entity.Container;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Staff view, including commercially sensitive fields. */
public record ContainerDetail(
		ContainerSummary container,
		BigDecimal acquisitionCost,
		String acquisitionCurrency,
		String notes,
		String statusReason,
		Instant createdAt,
		long version,
		List<ContainerDocumentResponse> documents) {

	public static ContainerDetail from(Container c, List<ContainerDocumentResponse> documents) {
		return new ContainerDetail(ContainerSummary.from(c), c.getAcquisitionCost(), c.getAcquisitionCurrency(),
				c.getNotes(), c.getStatusReason(), c.getCreatedAt(), c.getVersion(), documents);
	}

}
