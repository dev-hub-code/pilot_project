package com.sealease.backend.marketplace.dto;

import java.util.List;
import java.util.UUID;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.ProductStatus;

/**
 * A container listing as the open API shows it: its published terms and photos, nothing about buyers or staff. Rent is
 * set by each order's total value (the rent bands), so none is given here.
 *
 * @param status        OPEN (on sale) or CLOSED (sold out or no longer sold)
 * @param price         per container
 * @param description   {@code null} in lists
 * @param photoIds      photos of containers of the plan's type; empty in lists
 */
public record OpenListing(
		String code,
		String title,
		String summary,
		String description,
		ProductStatus status,
		ContainerType containerType,
		String containerTypeLabel,
		MoneyResponse price,
		int minTenureYears,
		int maxTenureYears,
		List<UUID> photoIds) {

	public static OpenListing from(MarketplaceListing l) {
		return new OpenListing(l.code(), l.title(), l.summary(), null, l.status(), l.containerType(),
				l.containerType().label(), l.price(), l.minTenureYears(), l.maxTenureYears(),
				l.coverPhotoId() == null ? List.of() : List.of(l.coverPhotoId()));
	}

	public static OpenListing from(ProductResponse p, List<UUID> photoIds) {
		return new OpenListing(p.code(), p.title(), p.summary(), p.description(), p.status(), p.containerType(),
				p.containerType().label(), p.price(), p.minTenureYears(), p.maxTenureYears(), List.copyOf(photoIds));
	}

}
