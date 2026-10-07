package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.container.service.ContainerAllocationService;
import com.sealease.backend.investment.dto.ProductRequest;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.dto.ProductSearchCriteria;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Staff management of investment plans: drafting, publishing, closing and cancelling. */
@Service
public class ProductService {

	private static final String ENTITY = "INVESTMENT_PRODUCT";
	/** Plans that have been in front of investors. */
	private static final Set<ProductStatus> PUBLISHED = EnumSet.of(ProductStatus.OPEN, ProductStatus.CLOSED);

	private final InvestmentProductRepository products;
	private final HoldingService holdings;
	private final ContainerAllocationService inventory;
	private final AuditService audit;
	private final Clock clock;

	public ProductService(InvestmentProductRepository products, HoldingService holdings,
			ContainerAllocationService inventory, AuditService audit, Clock clock) {
		this.products = products;
		this.holdings = holdings;
		this.inventory = inventory;
		this.audit = audit;
		this.clock = clock;
	}

	/** A new draft plan. */
	@Transactional
	public ProductResponse create(UUID actorId, ProductRequest request) {
		InvestmentProduct product = new InvestmentProduct("PLAN-" + products.nextCodeNumber(), actorId);
		product.defineTerms(validatedTerms(request));
		products.saveAndFlush(product);
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_CREATED, ENTITY, product.getId())
			.withNewValue(snapshot(product)));
		return view(product);
	}

	/** Terms are editable only while DRAFT: once published, investors rely on them. */
	@Transactional
	public ProductResponse update(UUID actorId, UUID productId, ProductRequest request) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.DRAFT) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only draft plans can be edited");
		}
		Map<String, Object> before = snapshot(product);
		product.defineTerms(validatedTerms(request));
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_UPDATED, ENTITY, productId)
			.withOldValue(before)
			.withNewValue(snapshot(product)));
		return view(product);
	}

	@Transactional
	public ProductResponse publish(UUID actorId, UUID productId) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.DRAFT) {
			throw new BusinessException(ErrorCode.CONFLICT, "Plan is already " + product.getStatus());
		}
		product.publish(actorId, clock.instant());
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_PUBLISHED, ENTITY, productId)
			.withNewValue(Map.of("code", product.getCode())));
		return view(product);
	}

	/**
	 * Stops selling containers under the plan. Orders already placed can still be paid, and sold
	 * containers keep their lease and payouts.
	 */
	@Transactional
	public ProductResponse close(UUID actorId, UUID productId) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.OPEN) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only open plans can be closed");
		}
		product.close(clock.instant());
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_CLOSED, ENTITY, productId)
			.withOldValue(Map.of("status", ProductStatus.OPEN))
			.withNewValue(Map.of("status", ProductStatus.CLOSED)));
		return view(product);
	}

	/** A draft that will never be published. Published plans are closed instead. */
	@Transactional
	public ProductResponse cancel(UUID actorId, UUID productId, String reason) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.DRAFT) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Only draft plans can be cancelled; close a published plan instead");
		}
		product.cancel(reason.strip(), clock.instant());
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_CANCELLED, ENTITY, productId)
			.withOldValue(Map.of("status", ProductStatus.DRAFT))
			.withNewValue(Map.of("status", ProductStatus.CANCELLED, "reason", reason.strip())));
		return view(product);
	}

	/** Every plan that has been published (not drafts or cancelled ones), by code: for reports. */
	@Transactional(readOnly = true)
	public List<ProductResponse> published() {
		return views(products.findAll((root, query, cb) -> root.get("status").in(PUBLISHED), Sort.by("code")));
	}

	@Transactional(readOnly = true)
	public Page<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
		Page<InvestmentProduct> page = products.findAll(matching(criteria), pageable);
		Map<UUID, ProductResponse> byId = views(page.getContent()).stream()
			.collect(Collectors.toMap(ProductResponse::id, Function.identity()));
		return page.map(p -> byId.get(p.getId()));
	}

	@Transactional(readOnly = true)
	public ProductResponse detail(UUID productId) {
		return view(load(productId));
	}

	private ProductResponse view(InvestmentProduct product) {
		return views(List.of(product)).getFirst();
	}

	private List<ProductResponse> views(Collection<InvestmentProduct> page) {
		Map<UUID, Long> sold = holdings.containersSold(page.stream().map(InvestmentProduct::getId).toList());
		Map<ContainerType, Long> available = inventory.availableByType();
		return page.stream()
			.map(p -> ProductResponse.from(p, available.getOrDefault(p.getContainerType(), 0L),
					sold.getOrDefault(p.getId(), 0L)))
			.toList();
	}

	private static ProductTerms validatedTerms(ProductRequest r) {
		Currency currency = Currency.getInstance(r.currency());
		List<String> problems = new ArrayList<>();
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		if (r.price().stripTrailingZeros().scale() > digits) {
			problems.add("Amounts in " + currency.getCurrencyCode() + " allow at most " + digits + " decimals");
		}
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, String.join("; ", problems));
		}
		return new ProductTerms(r.containerType(), r.title().strip(), r.summary().strip(), r.description().strip(),
				currency, r.price(), r.monthlyRentPercent(), ProductTerms.capitalReturnPercent(r.tenureMonths()), r.tenureMonths(),
				r.riskDisclosure().strip(), r.termsAndConditions().strip());
	}

	private static Map<String, Object> snapshot(InvestmentProduct p) {
		ProductTerms t = p.terms();
		return Map.of("code", p.getCode(), "containerType", t.containerType(), "currency", t.currency().getCurrencyCode(),
				"price", t.price().toPlainString(), "monthlyRentPercent", t.monthlyRentPercent().toPlainString(),
				"monthlyCapitalReturnPercent", t.monthlyCapitalReturnPercent().toPlainString(),
				"tenureMonths", t.tenureMonths());
	}

	private InvestmentProduct load(UUID id) {
		return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plan", id));
	}

	private InvestmentProduct lock(UUID id) {
		return products.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Plan", id));
	}

	private static Specification<InvestmentProduct> matching(ProductSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.q() != null && !c.q().isBlank()) {
				String like = "%" + c.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("code")), like, '\\'),
						cb.like(cb.lower(root.get("title")), like, '\\')));
			}
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.containerType() != null) {
				predicates.add(cb.equal(root.get("containerType"), c.containerType()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
