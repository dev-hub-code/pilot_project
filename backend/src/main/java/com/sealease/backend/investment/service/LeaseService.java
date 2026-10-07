package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.investment.dto.HoldingShare;
import com.sealease.backend.investment.dto.Lease;
import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.repository.HoldingRepository;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Offerings on lease and their holders, for rental distribution by the earnings module. */
@Service
public class LeaseService {

	private static final String ENTITY = "INVESTMENT_PRODUCT";

	private final InvestmentProductRepository products;
	private final HoldingRepository holdings;
	private final AuditService audit;
	private final Clock clock;

	public LeaseService(InvestmentProductRepository products, HoldingRepository holdings, AuditService audit,
			Clock clock) {
		this.products = products;
		this.holdings = holdings;
		this.audit = audit;
		this.clock = clock;
	}

	/** Locks the offering row, serialising distributions (and maturity) of one lease. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Lease lock(UUID productId) {
		return Lease.of(products.findByIdForUpdate(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Investment product", productId)));
	}

	@Transactional(readOnly = true)
	public Lease lease(UUID productId) {
		return Lease.of(products.findById(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Investment product", productId)));
	}

	@Transactional(readOnly = true)
	public Map<UUID, Lease> leases(Collection<UUID> productIds) {
		return products.findAllById(productIds).stream()
			.map(Lease::of)
			.collect(Collectors.toMap(Lease::productId, Function.identity()));
	}

	@Transactional(readOnly = true)
	public List<Lease> active() {
		return products.findByStatusOrderByCode(ProductStatus.ACTIVE).stream().map(Lease::of).toList();
	}

	/** The holdings that share in the offering's rental, in a stable order. */
	@Transactional(readOnly = true)
	public List<HoldingShare> holders(UUID productId) {
		return holdings.findByProductIdAndStatusOrderById(productId, HoldingStatus.ACTIVE).stream()
			.map(h -> new HoldingShare(h.getId(), h.getUserId(), h.amount(), h.getOwnershipPercent()))
			.toList();
	}

	/** Every rental period has been distributed: the offering and its holdings mature. Caller holds the lock. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void mature(UUID productId) {
		InvestmentProduct product = products.findByIdForUpdate(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Investment product", productId));
		List<Holding> held = holdings.findByProductIdAndStatusOrderById(productId, HoldingStatus.ACTIVE);
		held.forEach(Holding::mature);
		product.mature(clock.instant());
		products.flush();
		audit.record(AuditRecord.of(null, AuditAction.PRODUCT_MATURED, ENTITY, productId)
			.withOldValue(Map.of("status", ProductStatus.ACTIVE))
			.withNewValue(Map.of("status", ProductStatus.MATURED, "holdings", held.size())));
	}

}
