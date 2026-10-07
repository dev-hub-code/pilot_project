package com.sealease.backend.marketplace.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.container.service.ContainerService;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import com.sealease.backend.investment.service.EligibilityService;
import com.sealease.backend.investment.service.OfferingQuery;
import com.sealease.backend.investment.service.ProductService;
import com.sealease.backend.marketplace.dto.MarketplaceCriteria;
import com.sealease.backend.marketplace.dto.MarketplaceDetail;
import com.sealease.backend.marketplace.dto.MarketplaceListing;
import com.sealease.backend.marketplace.dto.MarketplaceSort;
import com.sealease.backend.marketplace.dto.ReturnProjection;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Investor-facing read side of plans. Drafts and cancelled plans never appear. Photos are those of
 * containers of the plan's type that are marked visible to investors.
 */
@Service
public class MarketplaceService {

	private static final int PHOTOS = 6;

	private final InvestmentProductRepository products;
	private final ProductService plans;
	private final OfferingQuery offerings;
	private final ContainerService containers;
	private final EligibilityService eligibility;

	public MarketplaceService(InvestmentProductRepository products, ProductService plans, OfferingQuery offerings,
			ContainerService containers, EligibilityService eligibility) {
		this.products = products;
		this.plans = plans;
		this.offerings = offerings;
		this.containers = containers;
		this.eligibility = eligibility;
	}

	@Transactional(readOnly = true)
	public Page<MarketplaceListing> search(MarketplaceCriteria criteria, Pageable pageable) {
		ProductStatus status = criteria.status() == null ? ProductStatus.OPEN : criteria.status();
		if (!status.isListed()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported status filter");
		}
		Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		Page<InvestmentProduct> page = products.findAll(listed(criteria, status), unsorted);
		Map<ContainerType, UUID> covers = new EnumMap<>(ContainerType.class);
		return page.map(p -> MarketplaceListing.from(plans.detail(p.getId()),
				covers.computeIfAbsent(p.getContainerType(), this::cover)));
	}

	@Transactional(readOnly = true)
	public MarketplaceDetail detail(UUID productId, UUID viewerId) {
		InvestmentProduct product = listedProduct(productId);
		ProductResponse response = plans.detail(productId);
		List<UUID> photos = containers.photosOfType(product.getContainerType(), PHOTOS);
		return MarketplaceDetail.from(response, MarketplaceListing.from(response, photos.isEmpty() ? null : photos.getFirst()),
				photos, eligibility.evaluate(viewerId, product));
	}

	@Transactional(readOnly = true)
	public ReturnProjection project(UUID productId, UUID viewerId, int quantity) {
		ProductTerms t = listedProduct(productId).terms();
		BigDecimal n = BigDecimal.valueOf(Math.max(quantity, 0));
		BigDecimal months = BigDecimal.valueOf(t.tenureMonths());
		List<String> problems = offerings.problems(viewerId, productId, quantity);
		Money rent = t.monthlyRent().times(n);
		Money capital = t.monthlyCapitalReturn().times(n);
		return new ReturnProjection(quantity, MoneyResponse.from(t.pricePerContainer().times(n)), MoneyResponse.from(rent),
				MoneyResponse.from(capital), MoneyResponse.from(rent.plus(capital)), t.tenureMonths(),
				MoneyResponse.from(rent.times(months)), MoneyResponse.from(t.pricePerContainer().times(n)),
				MoneyResponse.from(rent.times(months).plus(t.pricePerContainer().times(n))), problems.isEmpty(), problems);
	}

	@Transactional(readOnly = true)
	public DocumentContent photo(UUID productId, UUID documentId) {
		InvestmentProduct product = listedProduct(productId);
		return containers.investorPhotoOfType(product.getContainerType(), documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
	}

	private UUID cover(ContainerType type) {
		List<UUID> photos = containers.photosOfType(type, 1);
		return photos.isEmpty() ? null : photos.getFirst();
	}

	private InvestmentProduct listedProduct(UUID productId) {
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed())
			.orElseThrow(() -> new ResourceNotFoundException("Plan", productId));
	}

	private static Specification<InvestmentProduct> listed(MarketplaceCriteria c, ProductStatus status) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("status"), status));
			if (c.containerType() != null) {
				predicates.add(cb.equal(root.get("containerType"), c.containerType()));
			}
			boolean countQuery = query != null && Long.class == query.getResultType();
			if (!countQuery && query != null) {
				MarketplaceSort sort = c.sort() == null ? MarketplaceSort.NEWEST : c.sort();
				switch (sort) {
					case HIGHEST_RETURN -> query.orderBy(cb.desc(root.get("monthlyRentPercent")), cb.desc(root.get("publishedAt")));
					case LOWEST_PRICE -> query.orderBy(cb.asc(root.get("price")), cb.desc(root.get("publishedAt")));
					case NEWEST -> query.orderBy(cb.desc(root.get("publishedAt")));
				}
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
