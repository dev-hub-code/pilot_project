package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.service.ContainerAllocationService;
import com.sealease.backend.container.service.ContainerService;
import com.sealease.backend.investment.dto.HoldingLabel;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.dto.PortfolioResponse;
import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.investment.repository.HoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Confirmed investments (one container each), their leases and the investor's portfolio view. */
@Service
public class HoldingService {

	private static final String ENTITY = "HOLDING";

	private final HoldingRepository holdings;
	private final OfferingQuery offerings;
	private final ContainerService containers;
	private final ContainerAllocationService inventory;
	private final AuditService audit;

	public HoldingService(HoldingRepository holdings, OfferingQuery offerings, ContainerService containers,
			ContainerAllocationService inventory, AuditService audit) {
		this.holdings = holdings;
		this.offerings = offerings;
		this.containers = containers;
		this.inventory = inventory;
		this.audit = audit;
	}

	/**
	 * Called by order confirmation, in its transaction, once the container is leased to the
	 * investor: the lease (and the payout schedule) starts on {@code leaseStartsOn}.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public PayoutTerms record(UUID userId, UUID productId, UUID orderId, UUID orderItemId, UUID containerId,
			Money amount, BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent, int tenureMonths,
			LocalDate leaseStartsOn, Instant confirmedAt) {
		Holding holding = holdings.saveAndFlush(new Holding(userId, productId, orderId, orderItemId, containerId, amount,
				monthlyRentPercent, monthlyCapitalReturnPercent, tenureMonths, leaseStartsOn, confirmedAt));
		return payoutTerms(holding);
	}

	/** The last payout has been paid: the holding matures and its container returns to inventory. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void mature(UUID holdingId, Instant now) {
		Holding holding = holdings.findByIdForUpdate(holdingId)
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
		if (holding.getStatus() != HoldingStatus.ACTIVE) {
			return;
		}
		holding.mature(now);
		holdings.flush();
		inventory.endLease(holding.getContainerId());
		audit.record(AuditRecord.of(null, AuditAction.HOLDING_MATURED, ENTITY, holdingId)
			.withNewValue(Map.of("containerId", holding.getContainerId().toString(),
					"leaseEndsOn", holding.getLeaseEndsOn().toString())));
	}

	@Transactional(readOnly = true)
	public PortfolioResponse portfolio(UUID userId) {
		return summarise(holdings.findByUserIdOrderByConfirmedAtDesc(userId));
	}

	@Transactional(readOnly = true)
	public List<HoldingResponse> forOrder(UUID orderId) {
		return summarise(holdings.findByOrderIdOrderByCreatedAt(orderId)).holdings();
	}

	/** For support tickets: a label for one of the user's own holdings, e.g. "PLAN-10001 · SLSU1234565". */
	@Transactional(readOnly = true)
	public Optional<String> ownedLabel(UUID userId, UUID holdingId) {
		return holdings.findById(holdingId)
			.filter(h -> h.getUserId().equals(userId))
			.map(h -> offerings.terms(List.of(h.getProductId())).get(h.getProductId()).code() + " · "
					+ containers.summary(h.getContainerId()).containerNumber());
	}

	@Transactional(readOnly = true)
	public Map<UUID, HoldingLabel> labels(Collection<UUID> holdingIds) {
		List<Holding> found = holdings.findAllById(holdingIds);
		Map<UUID, OfferingTerms> plans = offerings.terms(found.stream().map(Holding::getProductId).distinct().toList());
		Map<UUID, ContainerSummary> boxes = containers.summaries(found.stream().map(Holding::getContainerId).toList());
		Map<UUID, HoldingLabel> labels = new HashMap<>();
		for (Holding h : found) {
			OfferingTerms plan = plans.get(h.getProductId());
			labels.put(h.getId(), new HoldingLabel(h.getId(), plan.code(), plan.terms().title(),
					boxes.get(h.getContainerId()).containerNumber(), h.getTenureMonths(), h.amount()));
		}
		return labels;
	}

	/** Distinct investors per plan. */
	@Transactional(readOnly = true)
	public Map<UUID, Long> investorCounts(Collection<UUID> productIds) {
		return counts(productIds.isEmpty() ? List.of() : holdings.countInvestorsByProduct(productIds));
	}

	/** Containers sold per plan. */
	@Transactional(readOnly = true)
	public Map<UUID, Long> containersSold(Collection<UUID> productIds) {
		return counts(productIds.isEmpty() ? List.of() : holdings.countContainersByProduct(productIds));
	}

	/** Capital invested across all holdings, per currency. */
	@Transactional(readOnly = true)
	public Map<String, Money> totalInvested() {
		Map<String, Money> totals = new TreeMap<>();
		for (Object[] row : holdings.sumAmountByCurrency()) {
			String currency = (String) row[0];
			totals.put(currency, Money.of((BigDecimal) row[1], Currency.getInstance(currency)));
		}
		return totals;
	}

	private PayoutTerms payoutTerms(Holding h) {
		OfferingTerms plan = offerings.terms(List.of(h.getProductId())).get(h.getProductId());
		return new PayoutTerms(h.getId(), h.getUserId(), h.getProductId(), plan.code(),
				containers.summary(h.getContainerId()).containerNumber(), h.amount(), h.monthlyRent(),
				h.getTenureMonths(), h.getLeaseStartsOn());
	}

	private PortfolioResponse summarise(List<Holding> owned) {
		Map<UUID, OfferingTerms> plans = offerings.terms(owned.stream().map(Holding::getProductId).distinct().toList());
		Map<UUID, ContainerSummary> boxes = containers.summaries(owned.stream().map(Holding::getContainerId).toList());
		Map<String, Money> totals = new TreeMap<>();
		int active = 0;
		for (Holding h : owned) {
			totals.merge(h.amount().currency().getCurrencyCode(), h.amount(), Money::plus);
			active += h.getStatus() == HoldingStatus.ACTIVE ? 1 : 0;
		}
		return new PortfolioResponse(totals.values().stream().map(MoneyResponse::from).toList(), active,
				owned.stream()
					.map(h -> HoldingResponse.from(h, plans.get(h.getProductId()), boxes.get(h.getContainerId())))
					.toList());
	}

	private static Map<UUID, Long> counts(List<Object[]> rows) {
		Map<UUID, Long> counts = new HashMap<>();
		for (Object[] row : rows) {
			counts.put((UUID) row[0], (Long) row[1]);
		}
		return counts;
	}

}
