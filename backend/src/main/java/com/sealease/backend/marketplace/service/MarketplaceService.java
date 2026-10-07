package com.sealease.backend.marketplace.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.service.ContainerService;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.entity.RentalFrequency;
import com.sealease.backend.investment.repository.CapacityMovementRepository;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import com.sealease.backend.investment.service.EligibilityService;
import com.sealease.backend.investment.service.InvestmentAmountPolicy;
import com.sealease.backend.investment.service.InvestorEligibility;
import com.sealease.backend.marketplace.dto.MarketplaceCriteria;
import com.sealease.backend.marketplace.dto.MarketplaceDetail;
import com.sealease.backend.marketplace.dto.MarketplaceListing;
import com.sealease.backend.marketplace.dto.MarketplaceSort;
import com.sealease.backend.marketplace.dto.ReturnProjection;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Investor-facing read side of offerings. Drafts and cancelled offerings never appear; documents
 * are served only when explicitly marked visible to investors.
 */
@Service
public class MarketplaceService {

	private final InvestmentProductRepository products;
	private final CapacityMovementRepository movements;
	private final ContainerService containers;
	private final EligibilityService eligibility;

	public MarketplaceService(InvestmentProductRepository products, CapacityMovementRepository movements,
			ContainerService containers, EligibilityService eligibility) {
		this.products = products;
		this.movements = movements;
		this.containers = containers;
		this.eligibility = eligibility;
	}

	@Transactional(readOnly = true)
	public Page<MarketplaceListing> search(MarketplaceCriteria criteria, Pageable pageable) {
		ProductStatus status = criteria.status() == null ? ProductStatus.OPEN : criteria.status();
		if (!status.isListed()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported status filter");
		}
		List<UUID> containerIds = criteria.containerType() == null ? null : containers.idsOfType(criteria.containerType());
		Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		Page<InvestmentProduct> page = products.findAll(listed(criteria, status, containerIds), unsorted);

		List<UUID> pageContainers = page.map(InvestmentProduct::getContainerId).toList();
		Map<UUID, ContainerSummary> summaries = containers.summaries(pageContainers);
		Map<UUID, UUID> covers = containers.coverPhotos(pageContainers);
		return page.map(p -> MarketplaceListing.from(ProductResponse.from(p, summaries.get(p.getContainerId())),
				covers.get(p.getContainerId())));
	}

	@Transactional(readOnly = true)
	public MarketplaceDetail detail(UUID productId, UUID viewerId) {
		InvestmentProduct product = listedProduct(productId);
		ProductResponse response = ProductResponse.from(product, containers.summary(product.getContainerId()));
		MarketplaceListing listing = MarketplaceListing.from(response,
				containers.coverPhotos(List.of(product.getContainerId())).get(product.getContainerId()));
		return MarketplaceDetail.from(response, listing, containers.investorDocuments(product.getContainerId()),
				eligibility.evaluate(viewerId, product));
	}

	@Transactional(readOnly = true)
	public ReturnProjection project(UUID productId, UUID viewerId, BigDecimal requested) {
		InvestmentProduct product = listedProduct(productId);
		ProductTerms terms = product.terms();
		Money amount = Money.of(requested, terms.currency());
		Money exposure = Money.of(movements.exposureOf(productId, viewerId), terms.currency());

		List<String> problems = new ArrayList<>();
		InvestorEligibility verdict = eligibility.evaluate(viewerId, product);
		problems.addAll(verdict.reasons());
		problems.addAll(InvestmentAmountPolicy.violations(terms, amount, product.available(), exposure));

		Money perPayment = amount.isPositive() ? InvestmentAmountPolicy.rentalShare(terms, amount) : Money.zero(terms.currency());
		RentalFrequency frequency = terms.rentalFrequency();
		int payments = terms.durationMonths() / frequency.monthsPerPeriod();
		return new ReturnProjection(MoneyResponse.from(amount),
				amount.isPositive() ? InvestmentAmountPolicy.ownershipPercent(terms, amount) : BigDecimal.ZERO,
				MoneyResponse.from(perPayment),
				MoneyResponse.from(perPayment.times(BigDecimal.valueOf(frequency.periodsPerYear()))),
				payments, MoneyResponse.from(perPayment.times(BigDecimal.valueOf(payments))), problems.isEmpty(),
				problems);
	}

	@Transactional(readOnly = true)
	public DocumentContent document(UUID productId, UUID documentId) {
		InvestmentProduct product = listedProduct(productId);
		return containers.investorDocument(product.getContainerId(), documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
	}

	private InvestmentProduct listedProduct(UUID productId) {
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed())
			.orElseThrow(() -> new ResourceNotFoundException("Offering", productId));
	}

	private static Specification<InvestmentProduct> listed(MarketplaceCriteria c, ProductStatus status,
			List<UUID> containerIds) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("status"), status));
			if (c.investmentType() != null) {
				predicates.add(cb.equal(root.get("investmentType"), c.investmentType()));
			}
			if (c.riskLevel() != null) {
				predicates.add(cb.equal(root.get("riskLevel"), c.riskLevel()));
			}
			if (containerIds != null) {
				predicates.add(containerIds.isEmpty() ? cb.disjunction() : root.get("containerId").in(containerIds));
			}
			boolean countQuery = query != null && Long.class == query.getResultType();
			if (!countQuery && query != null) {
				Expression<BigDecimal> total = root.get("totalAmount");
				MarketplaceSort sort = c.sort() == null ? MarketplaceSort.NEWEST : c.sort();
				switch (sort) {
					case HIGHEST_YIELD -> {
						Expression<Integer> periods = cb.<Integer>selectCase()
							.when(cb.equal(root.get("rentalFrequency"), RentalFrequency.MONTHLY), 12)
							.otherwise(4);
						query.orderBy(cb.desc(cb.quot(cb.prod(root.<BigDecimal>get("expectedRentalAmount"), periods), total)),
								cb.desc(root.get("publishedAt")));
					}
					case MOST_AVAILABLE -> query.orderBy(cb.desc(cb.diff(cb.diff(total,
							root.<BigDecimal>get("committedAmount")), root.<BigDecimal>get("reservedAmount"))));
					case LOWEST_MINIMUM -> query.orderBy(cb.asc(root.get("minimumInvestment")), cb.desc(root.get("publishedAt")));
					case NEWEST -> query.orderBy(cb.desc(root.get("publishedAt")));
				}
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
