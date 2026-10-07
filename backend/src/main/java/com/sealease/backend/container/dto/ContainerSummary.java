package com.sealease.backend.container.dto;

import com.sealease.backend.container.entity.Container;
import com.sealease.backend.container.entity.ContainerCondition;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;

import java.math.BigDecimal;
import java.util.UUID;

/** Container facts safe to show investors (no acquisition cost or internal notes). */
public record ContainerSummary(
		UUID id,
		String containerNumber,
		ContainerType containerType,
		ContainerCondition condition,
		ContainerStatus status,
		BigDecimal capacityCbm,
		int maxGrossKg,
		int tareKg,
		int manufactureYear,
		String manufacturer,
		String currentLocation,
		String locationCountry) {

	public static ContainerSummary from(Container c) {
		return new ContainerSummary(c.getId(), c.getContainerNumber(), c.getContainerType(), c.getCondition(),
				c.getStatus(), c.getCapacityCbm(), c.getMaxGrossKg(), c.getTareKg(), c.getManufactureYear(),
				c.getManufacturer(), c.getCurrentLocation(), c.getLocationCountry());
	}

}
