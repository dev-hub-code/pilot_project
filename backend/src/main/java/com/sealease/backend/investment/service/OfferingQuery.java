package com.sealease.backend.investment.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read access to plans for the order module, and the check of an investment (eligibility, units and limit). */
@Service
public class OfferingQuery {

	private final InvestmentProductRepository products;
	private final EligibilityService eligibility;

	public OfferingQuery(InvestmentProductRepository products, EligibilityService eligibility) {
		this.products = products;
		this.eligibility = eligibility;
	}

	@Transactional(readOnly = true)
	public OfferingTerms terms(UUID productId) {
		return OfferingTerms.of(load(productId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, OfferingTerms> terms(Collection<UUID> productIds) {
		return products.findAllById(productIds).stream()
			.map(OfferingTerms::of)
			.collect(Collectors.toMap(OfferingTerms::id, Function.identity()));
	}

	/** Why the investor cannot (currently) buy these units of the plan; empty when they can. */
	@Transactional(readOnly = true)
	public List<String> problems(UUID investorId, UUID productId, int units) {
		InvestmentProduct product = load(productId);
		List<String> problems = new ArrayList<>(eligibility.evaluate(investorId, product).reasons());
		problems.addAll(product.terms().unitProblems(units));
		if (product.terms().amountFor(units).amount().compareTo(PlanRules.MAX_INVESTMENT) > 0) {
			problems.add("That is more than one investment can be; buy fewer units");
		}
		return problems;
	}

	private InvestmentProduct load(UUID productId) {
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed())
			.orElseThrow(() -> new ResourceNotFoundException("Plan", productId));
	}

}
