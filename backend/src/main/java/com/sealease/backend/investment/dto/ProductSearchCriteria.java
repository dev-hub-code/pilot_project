package com.sealease.backend.investment.dto;

import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.entity.ProductStatus;

public record ProductSearchCriteria(String q, ProductStatus status, ContainerType containerType) {
}
