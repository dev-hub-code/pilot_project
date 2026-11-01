package com.sealease.backend.investment.dto;

import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.investment.entity.ProductStatus;

public record ProductSearchCriteria(String q, ProductStatus status, ReferralScheme audience) {
}
