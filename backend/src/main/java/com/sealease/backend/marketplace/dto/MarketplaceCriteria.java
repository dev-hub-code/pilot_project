package com.sealease.backend.marketplace.dto;

import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.ProductStatus;

/** Investor-facing filters. {@code status} is restricted to listed statuses; default OPEN. */
public record MarketplaceCriteria(ContainerType containerType, ProductStatus status, MarketplaceSort sort) {
}
