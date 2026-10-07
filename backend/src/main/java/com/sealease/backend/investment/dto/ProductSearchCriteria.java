package com.sealease.backend.investment.dto;

import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;

public record ProductSearchCriteria(String q, ProductStatus status, InvestmentType investmentType) {
}
