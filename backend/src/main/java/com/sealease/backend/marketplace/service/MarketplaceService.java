package com.sealease.backend.marketplace.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import com.sealease.backend.investment.service.EligibilityService;
import com.sealease.backend.investment.service.OfferingQuery;
import com.sealease.backend.investment.service.PlanPhotoService;
import com.sealease.backend.investment.service.ProductService;
import com.sealease.backend.marketplace.dto.MarketplaceCriteria;
import com.sealease.backend.marketplace.dto.MarketplaceDetail;
import com.sealease.backend.marketplace.dto.MarketplaceListing;
import com.sealease.backend.marketplace.dto.PublicPlan;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Investor-facing read side of plans. Drafts and cancelled plans never appear. Photos are the plan's own, uploaded
 * by admins.
 */
@Service
public class MarketplaceService {

	private static final int PHOTOS = PlanPhotoService.MAX_PHOTOS;

	private final InvestmentProductRepository products;
	private final ProductService plans;
	private final OfferingQuery offerings;
	private final PlanPhotoService photos;
	private final EligibilityService eligibility;

	public MarketplaceService(InvestmentProductRepository products, ProductService plans, OfferingQuery offerings,
			PlanPhotoService photos, EligibilityService eligibility) {
		this.products = products;
		this.plans = plans;
		this.offerings = offerings;
		this.photos = photos;
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
		Map<UUID, UUID> covers = photos.covers(page.map(InvestmentProduct::getId).toList());
		return page.map(p -> MarketplaceListing.from(plans.detail(p.getId()), covers.get(p.getId())));
	}

	/** Open plans for the public website, newest first: their published terms only. */
	@Transactional(readOnly = true)
	public List<PublicPlan> publicPlans(int limit) {
		return search(new MarketplaceCriteria(null, ProductStatus.OPEN, MarketplaceSort.NEWEST), PageRequest.of(0, limit))
			.map(PublicPlan::from).toList();
	}

	@Transactional(readOnly = true)
	public MarketplaceDetail detail(UUID productId, UUID viewerId) {
		InvestmentProduct product = listedProduct(productId);
		ProductResponse response = plans.detail(productId);
		List<UUID> shown = photos.documentIds(productId, PHOTOS);
		return MarketplaceDetail.from(response, MarketplaceListing.from(response, shown.isEmpty() ? null : shown.getFirst()),
				shown, eligibility.evaluate(viewerId, product));
	}

	/** @param tenureYears the lease in whole years; {@code null} for the plan's shortest */
	@Transactional(readOnly = true)
	public ReturnProjection project(UUID productId, UUID viewerId, int quantity, Integer tenureYears) {
		ProductTerms t = listedProduct(productId).terms();
		int years = tenureYears == null ? t.minTenureYears() : tenureYears;
		BigDecimal n = BigDecimal.valueOf(Math.max(quantity, 0));
		List<String> problems = offerings.problems(viewerId, productId, quantity, years);
		// Plans pay rent only, for every month of the lease the investor chooses.
		Money rent = t.monthlyRent().times(n);
		return new ReturnProjection(quantity, years, t.minTenureYears(), t.maxTenureYears(),
				MoneyResponse.from(t.pricePerContainer().times(n)), MoneyResponse.from(t.gstPerContainer().times(n)),
				MoneyResponse.from(t.pricePerContainerWithGst().times(n)), MoneyResponse.from(rent), ProductTerms.monthsOf(years),
				MoneyResponse.from(t.totalPayout(years).times(n)), problems.isEmpty(), problems);
	}

	@Transactional(readOnly = true)
	public DocumentContent photo(UUID productId, UUID documentId) {
		listedProduct(productId);
		return photos.photo(productId, documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
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
