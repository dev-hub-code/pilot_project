package com.sealease.backend.investment.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.container.service.ContainerAllocationService;
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
 * Read access to plans for the cart and order modules, and a non-binding pre-check of a purchase.
 * The binding check runs again when containers are reserved at checkout.
 */
@Service
public class OfferingQuery {

	private final InvestmentProductRepository products;
	private final EligibilityService eligibility;
	private final ContainerAllocationService inventory;

	public OfferingQuery(InvestmentProductRepository products, EligibilityService eligibility,
			ContainerAllocationService inventory) {
		this.products = products;
		this.eligibility = eligibility;
		this.inventory = inventory;
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

	/** Why the investor cannot (currently) buy this many containers under the plan; empty when they can. */
	@Transactional(readOnly = true)
	public List<String> problems(UUID investorId, UUID productId, int quantity) {
		InvestmentProduct product = load(productId);
		List<String> problems = new ArrayList<>(eligibility.evaluate(investorId, product).reasons());
		problems.addAll(PlanRules.quantityProblems(quantity, inventory.available(product.getContainerType())));
		return problems;
	}

	private InvestmentProduct load(UUID productId) {
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed())
			.orElseThrow(() -> new ResourceNotFoundException("Plan", productId));
	}

}
