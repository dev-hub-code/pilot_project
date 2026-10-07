package com.sealease.backend.investment.dto;

import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;

import java.util.UUID;

/** A plan's identity and current terms, for modules that sell or snapshot it. */
public record OfferingTerms(UUID id, String code, ProductStatus status, ProductTerms terms) {

	public static OfferingTerms of(InvestmentProduct p) {
		return new OfferingTerms(p.getId(), p.getCode(), p.getStatus(), p.terms());
	}

}
