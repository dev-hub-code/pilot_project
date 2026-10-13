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

/**
 * Read access to plans for the cart and order modules, and the check of a purchase (eligibility and quantity),
 * run when adding to the cart and again at checkout. Stock does not limit sales: finance assigns containers after
 * payment.
 */
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

	/** Why the investor cannot (currently) buy this many containers under the plan for this lease; empty when they can. */
	@Transactional(readOnly = true)
	public List<String> problems(UUID investorId, UUID productId, int quantity, int tenureYears) {
		InvestmentProduct product = load(productId);
		List<String> problems = new ArrayList<>(eligibility.evaluate(investorId, product).reasons());
		problems.addAll(PlanRules.quantityProblems(quantity));
		problems.addAll(product.terms().tenureProblems(tenureYears));
		return problems;
	}

	private InvestmentProduct load(UUID productId) {
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed())
			.orElseThrow(() -> new ResourceNotFoundException("Plan", productId));
	}

}
