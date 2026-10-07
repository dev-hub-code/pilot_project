package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.service.ContainerService;
import com.sealease.backend.investment.dto.CapacityMovementResponse;
import com.sealease.backend.investment.dto.Lease;
import com.sealease.backend.investment.dto.ProductRequest;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.dto.ProductSearchCriteria;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.InvestmentType;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.repository.CapacityMovementRepository;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Staff management of offerings: drafting, publishing and cancelling. */
@Service
public class ProductService {

	private static final String ENTITY = "INVESTMENT_PRODUCT";

	private final InvestmentProductRepository products;
	private final CapacityMovementRepository movements;
	private final ContainerService containers;
	private final AuditService audit;
	private final Clock clock;

	public ProductService(InvestmentProductRepository products, CapacityMovementRepository movements,
			ContainerService containers, AuditService audit, Clock clock) {
		this.products = products;
		this.movements = movements;
		this.containers = containers;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public ProductResponse create(UUID actorId, ProductRequest request) {
		ContainerSummary container = containers.summary(request.containerId());
		if (container.status() == ContainerStatus.RETIRED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A retired container cannot back an offering");
		}
		if (products.existsLiveForContainer(request.containerId())) {
			throw new BusinessException(ErrorCode.CONFLICT, "This container already backs a live offering");
		}
		ProductTerms terms = validatedTerms(request);
		InvestmentProduct product = new InvestmentProduct("CONT-" + products.nextCodeNumber(), request.containerId(),
				actorId);
		product.defineTerms(terms);
		products.saveAndFlush(product);
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_CREATED, ENTITY, product.getId())
			.withNewValue(snapshot(product)));
		return ProductResponse.from(product, container);
	}

	/** Terms are editable only while DRAFT: once published, investors rely on them. */
	@Transactional
	public ProductResponse update(UUID actorId, UUID productId, ProductRequest request) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.DRAFT) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only draft offerings can be edited");
		}
		if (!product.getContainerId().equals(request.containerId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The container of an offering cannot change");
		}
		Map<String, Object> before = snapshot(product);
		product.defineTerms(validatedTerms(request));
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_UPDATED, ENTITY, productId)
			.withOldValue(before)
			.withNewValue(snapshot(product)));
		return detail(productId);
	}

	@Transactional
	public ProductResponse publish(UUID actorId, UUID productId) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.DRAFT) {
			throw new BusinessException(ErrorCode.CONFLICT, "Offering is already " + product.getStatus());
		}
		ContainerSummary container = containers.summary(product.getContainerId());
		if (container.status() == ContainerStatus.RETIRED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The container has been retired");
		}
		if (!containers.hasInvestorPhoto(product.getContainerId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Add at least one investor-visible container photo before publishing");
		}
		Instant now = clock.instant();
		Instant closes = product.terms().offerClosesAt();
		if (closes != null && !closes.isAfter(now)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The offer window has already closed");
		}
		product.publish(actorId, now);
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_PUBLISHED, ENTITY, productId)
			.withNewValue(Map.of("code", product.getCode(), "termsVersion", product.terms().termsVersion())));
		return ProductResponse.from(product, container);
	}

	/** Allowed while nothing has been reserved or invested; afterwards money is involved. */
	@Transactional
	public ProductResponse cancel(UUID actorId, UUID productId, String reason) {
		InvestmentProduct product = lock(productId);
		if (product.getStatus() != ProductStatus.DRAFT && product.getStatus() != ProductStatus.OPEN) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"A " + product.getStatus() + " offering cannot be cancelled");
		}
		if (!product.reserved().isZero() || !product.committed().isZero()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Investors have reserved or committed capital in this offering");
		}
		ProductStatus previous = product.getStatus();
		product.cancel(reason.strip(), clock.instant());
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_CANCELLED, ENTITY, productId)
			.withOldValue(Map.of("status", previous))
			.withNewValue(Map.of("status", ProductStatus.CANCELLED, "reason", reason.strip())));
		return detail(productId);
	}

	/**
	 * Starts the lease: the offering stops taking investments and rental periods run from
	 * {@code startsOn}. A FUNDED offering qualifies, and so does an OPEN one with confirmed investors
	 * and nothing reserved (its unsold share then earns for the platform, not for investors).
	 */
	@Transactional
	public ProductResponse activateLease(UUID actorId, UUID productId, LocalDate startsOn) {
		InvestmentProduct product = lock(productId);
		ProductStatus previous = product.getStatus();
		boolean openAndSettled = previous == ProductStatus.OPEN && product.committed().isPositive()
				&& product.reserved().isZero();
		if (previous != ProductStatus.FUNDED && !openAndSettled) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, previous == ProductStatus.OPEN
					? "Only offerings with confirmed investors and no orders awaiting payment can start their lease"
					: "A " + previous + " offering cannot start a lease");
		}
		ProductTerms terms = product.terms();
		if (Lease.periodCount(terms.rentalFrequency(), terms.durationMonths()) == 0) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The term is shorter than one rental period");
		}
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
		if (startsOn.isBefore(LocalDate.ofInstant(product.getPublishedAt(), ZoneOffset.UTC))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The lease cannot start before the offering was published");
		}
		if (startsOn.isAfter(today.plusYears(1))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The lease must start within a year");
		}
		product.activate(startsOn, actorId, now);
		products.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PRODUCT_LEASE_ACTIVATED, ENTITY, productId)
			.withOldValue(Map.of("status", previous))
			.withNewValue(Map.of("status", ProductStatus.ACTIVE, "leaseStartsOn", startsOn.toString(),
					"leaseEndsOn", product.getLeaseEndsOn().toString(),
					"committed", product.committed().toString())));
		return detail(productId);
	}

	@Transactional(readOnly = true)
	public Page<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
		Page<InvestmentProduct> page = products.findAll(matching(criteria), pageable);
		Map<UUID, ContainerSummary> byId = containers.summaries(page.map(InvestmentProduct::getContainerId).toList());
		return page.map(p -> ProductResponse.from(p, byId.get(p.getContainerId())));
	}

	@Transactional(readOnly = true)
	public ProductResponse detail(UUID productId) {
		InvestmentProduct product = load(productId);
		return ProductResponse.from(product, containers.summary(product.getContainerId()));
	}

	@Transactional(readOnly = true)
	public List<CapacityMovementResponse> recentMovements(UUID productId) {
		load(productId);
		return movements.findByProductIdOrderByCreatedAtDesc(productId, PageRequest.of(0, 50)).stream()
			.map(CapacityMovementResponse::from)
			.toList();
	}

	private ProductTerms validatedTerms(ProductRequest r) {
		Currency currency = Currency.getInstance(r.currency());
		BigDecimal total = r.totalAmount();
		boolean standalone = r.investmentType() == InvestmentType.HNI;
		BigDecimal minimum = standalone ? total : r.minimumInvestment();
		BigDecimal increment = standalone ? total : r.investmentIncrement();
		BigDecimal maximum = standalone ? null : r.maximumPerInvestor();
		if (minimum == null || increment == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Shared (retail) offerings need a minimum investment and an increment");
		}
		List<String> problems = new ArrayList<>();
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		for (BigDecimal amount : new BigDecimal[] { total, minimum, increment, maximum, r.expectedRentalAmount() }) {
			if (amount != null && amount.stripTrailingZeros().scale() > digits) {
				problems.add("Amounts in " + currency.getCurrencyCode() + " allow at most " + digits + " decimals");
				break;
			}
		}
		if (minimum.compareTo(total) > 0) {
			problems.add("Minimum investment cannot exceed the price");
		}
		if (increment.compareTo(total) > 0) {
			problems.add("Increment cannot exceed the price");
		}
		if (maximum != null && (maximum.compareTo(minimum) < 0 || maximum.compareTo(total) > 0)) {
			problems.add("Maximum per investor must be between the minimum and the price");
		}
		if (r.offerOpensAt() != null && r.offerClosesAt() != null && !r.offerClosesAt().isAfter(r.offerOpensAt())) {
			problems.add("Offer must close after it opens");
		}
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, String.join("; ", problems));
		}
		return new ProductTerms(r.investmentType(), r.title().strip(), r.summary().strip(), r.description().strip(),
				currency, total, minimum, increment, maximum, r.expectedRentalAmount(), r.rentalFrequency(),
				r.durationMonths(), r.managementFeePercent() == null ? BigDecimal.ZERO : r.managementFeePercent(),
				blankToNull(r.lesseeName()), r.riskLevel(), r.riskDisclosure().strip(),
				r.termsAndConditions().strip(), r.termsVersion().strip(), r.offerOpensAt(), r.offerClosesAt());
	}

	private static Map<String, Object> snapshot(InvestmentProduct p) {
		ProductTerms t = p.terms();
		return Map.of("code", p.getCode(), "type", t.investmentType(), "currency", t.currency().getCurrencyCode(),
				"price", t.totalAmount().toPlainString(), "minimum", t.minimumInvestment().toPlainString(),
				"rental", t.expectedRentalAmount().toPlainString() + "/" + t.rentalFrequency(),
				"durationMonths", t.durationMonths(), "managementFeePercent", t.managementFeePercent().toPlainString(),
				"termsVersion", t.termsVersion());
	}

	private InvestmentProduct load(UUID id) {
		return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Investment product", id));
	}

	private InvestmentProduct lock(UUID id) {
		return products.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Investment product", id));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
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
			if (c.investmentType() != null) {
				predicates.add(cb.equal(root.get("investmentType"), c.investmentType()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
