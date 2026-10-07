package com.sealease.backend.investment.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.repository.CapacityMovementRepository;
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
 * Read access to offerings for the cart and order modules, and a non-binding pre-check of an
 * investment. The binding check runs again when capacity is reserved.
 */
@Service
public class OfferingQuery {

	private final InvestmentProductRepository products;
	private final CapacityMovementRepository movements;
	private final EligibilityService eligibility;

	public OfferingQuery(InvestmentProductRepository products, CapacityMovementRepository movements,
			EligibilityService eligibility) {
		this.products = products;
		this.movements = movements;
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

	/** Why the investor cannot (currently) invest this amount; empty when they can. */
	@Transactional(readOnly = true)
	public List<String> problems(UUID investorId, UUID productId, Money amount) {
		InvestmentProduct product = load(productId);
		List<String> problems = new ArrayList<>(eligibility.evaluate(investorId, product).reasons());
		Money exposure = Money.of(movements.exposureOf(productId, investorId), product.currencyUnit());
		problems.addAll(InvestmentAmountPolicy.violations(product.terms(), amount, product.available(), exposure));
		return problems;
	}

	private InvestmentProduct load(UUID productId) {
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed())
			.orElseThrow(() -> new ResourceNotFoundException("Offering", productId));
	}

}
