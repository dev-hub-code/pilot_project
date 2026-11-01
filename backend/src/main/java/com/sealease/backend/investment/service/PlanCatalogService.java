package com.sealease.backend.investment.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.PlanDetail;
import com.sealease.backend.investment.dto.PlanQuote;
import com.sealease.backend.investment.dto.PlanView;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.referral.service.ReferralSchemeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The read side of plans for investors and the website: shortest tenure first. Drafts and cancelled plans never appear,
 * and investors see only the plans for their type.
 */
@Service
public class PlanCatalogService {

	private static final Sort BY_TENURE = Sort.by("tenureYears", "code");

	private final InvestmentProductRepository products;
	private final OfferingQuery offerings;
	private final EligibilityService eligibility;
	private final ReferralSchemeService schemes;

	public PlanCatalogService(InvestmentProductRepository products, OfferingQuery offerings,
			EligibilityService eligibility, ReferralSchemeService schemes) {
		this.products = products;
		this.offerings = offerings;
		this.eligibility = eligibility;
		this.schemes = schemes;
	}

	/** Plans for one investor type; open ones unless {@code status} asks for another listed status. */
	@Transactional(readOnly = true)
	public Page<PlanView> search(ReferralScheme audience, ProductStatus status, Pageable pageable) {
		ProductStatus wanted = status == null ? ProductStatus.OPEN : status;
		if (!wanted.isListed()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported status filter");
		}
		Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_TENURE);
		return products.findAll((root, query, cb) -> cb.and(cb.equal(root.get("status"), wanted),
				cb.equal(root.get("audience"), audience)), sorted).map(PlanView::of);
	}

	/** The plans for a viewer's investor type. */
	@Transactional(readOnly = true)
	public Page<PlanView> searchFor(UUID viewerId, ProductStatus status, Pageable pageable) {
		return search(schemes.schemeOf(viewerId), status, pageable);
	}

	/** Listed online plans for anyone, without ids. */
	@Transactional(readOnly = true)
	public Page<PlanView> openPlans(ProductStatus status, Pageable pageable) {
		return search(ReferralScheme.ONLINE, status, pageable).map(PlanView::withoutId);
	}

	/** Open online plans for the public website. */
	@Transactional(readOnly = true)
	public List<PlanView> publicPlans(int limit) {
		return openPlans(ProductStatus.OPEN, PageRequest.of(0, limit)).getContent();
	}

	/** A listed online plan by its code, for the open API. */
	@Transactional(readOnly = true)
	public PlanView openPlan(String code) {
		return products.findByCode(code)
			.filter(p -> p.getStatus().isListed() && p.getAudience() == ReferralScheme.ONLINE)
			.map(p -> PlanView.of(p).withoutId())
			.orElseThrow(() -> new ResourceNotFoundException("Plan", code));
	}

	@Transactional(readOnly = true)
	public PlanDetail detail(UUID productId, UUID viewerId) {
		InvestmentProduct product = listedProduct(productId, viewerId);
		return new PlanDetail(PlanView.of(product), eligibility.evaluate(viewerId, product));
	}

	/** What buying {@code units} units of a plan costs and pays. */
	@Transactional(readOnly = true)
	public PlanQuote quote(UUID productId, UUID viewerId, int units) {
		ProductTerms t = listedProduct(productId, viewerId).terms();
		List<String> problems = new ArrayList<>(offerings.problems(viewerId, productId, units));
		Money amount = t.amountFor(units);
		BigDecimal rate = t.monthlyReturnPercent();
		return new PlanQuote(units, MoneyResponse.from(Money.of(t.unitPrice(), t.currency())), MoneyResponse.from(amount),
				t.annualReturnPercent(), MoneyResponse.from(ProductTerms.monthlyRent(amount, rate)), ProductTerms.monthsOf(t.tenureYears()),
				MoneyResponse.from(ProductTerms.totalPayout(amount, t.tenureYears(), rate)), problems.isEmpty(), problems);
	}

	/** A plan the viewer may see: listed, and for their investor type. */
	private InvestmentProduct listedProduct(UUID productId, UUID viewerId) {
		ReferralScheme scheme = schemes.schemeOf(viewerId);
		return products.findById(productId)
			.filter(p -> p.getStatus().isListed() && p.getAudience() == scheme)
			.orElseThrow(() -> new ResourceNotFoundException("Plan", productId));
	}

}
