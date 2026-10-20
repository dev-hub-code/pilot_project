package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.entity.ContainerType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Confirmed investments (one container each), their leases and the investor's portfolio view. A paid investment
 * awaits its container until finance assigns one, then the investor's acceptance of the lease agreement; its lease and
 * payouts start when they accept.
 */
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
	 * investor: the lease (and the payout schedule) starts at {@code leaseStartsAt}, on the hour.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public PayoutTerms record(UUID userId, UUID productId, UUID orderId, UUID orderItemId, UUID containerId,
			Money amount, BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent, int tenureMonths,
			Instant leaseStartsAt, Instant confirmedAt, boolean paidAtOffice) {
		Holding holding = holdings.saveAndFlush(new Holding(userId, productId, orderId, orderItemId, containerId, amount,
				monthlyRentPercent, monthlyCapitalReturnPercent, tenureMonths, LocalDate.ofInstant(leaseStartsAt, ZoneOffset.UTC),
				confirmedAt, paidAtOffice));
		return payoutTerms(holding, leaseStartsAt);
	}

	/** Called by order confirmation, in its transaction: a paid investment waiting for finance to assign a container. */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID recordAwaitingContainer(UUID userId, UUID productId, UUID orderId, UUID orderItemId, Money amount,
			BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent, int tenureMonths, Instant confirmedAt,
			boolean paidAtOffice) {
		return holdings.saveAndFlush(new Holding(userId, productId, orderId, orderItemId, null, amount, monthlyRentPercent,
				monthlyCapitalReturnPercent, tenureMonths, null, confirmedAt, paidAtOffice)).getId();
	}

	/**
	 * Leases the container finance typed in (of the plan's type) to a holding awaiting one. The holding then awaits the
	 * investor's acceptance of the lease agreement; the lease and payouts start with {@link #startLease}.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public HoldingResponse assignContainer(UUID actorId, UUID holdingId, String containerNumber, String currentLocation,
			Instant now) {
		Holding holding = holdings.findByIdForUpdate(holdingId)
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
		if (holding.getStatus() != HoldingStatus.AWAITING_CONTAINER) {
			throw new BusinessException(ErrorCode.CONFLICT, "This investment already has a container");
		}
		requireTaskOf(actorId, holding);
		ContainerType type = offerings.terms(holding.getProductId()).terms().containerType();
		ContainerSummary container = inventory.assign(containerNumber, type, currentLocation, actorId);
		holding.assignContainer(container.id(), actorId, now);
		holdings.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.HOLDING_CONTAINER_ASSIGNED, ENTITY, holdingId)
			.withNewValue(Map.of("containerId", container.id().toString(), "containerNumber", container.containerNumber())));
		return summarise(List.of(holding)).holdings().getFirst();
	}

	/**
	 * The investor accepted the lease agreement of their holding awaiting acceptance: its lease runs from
	 * {@code leaseStartsAt} (on the hour); the caller schedules the payouts from the returned terms.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public PayoutTerms startLease(UUID userId, UUID holdingId, Instant leaseStartsAt) {
		Holding holding = holdings.findByIdForUpdate(holdingId)
			.filter(h -> h.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
		if (holding.getStatus() != HoldingStatus.AWAITING_ACCEPTANCE) {
			throw new BusinessException(ErrorCode.CONFLICT, holding.getStatus() == HoldingStatus.AWAITING_CONTAINER
					? "This investment has no container yet" : "This lease agreement has already been accepted");
		}
		holding.startLease(LocalDate.ofInstant(leaseStartsAt, ZoneOffset.UTC));
		holdings.flush();
		return payoutTerms(holding, leaseStartsAt);
	}

	/**
	 * A finance member takes the task of assigning an investment's container: from now on only they may assign it.
	 * Taking one already assigned to you changes nothing; one another member holds must be given back by them first.
	 */
	@Transactional
	public AwaitingContainer takeContainerTask(UUID actorId, UUID holdingId, Instant now) {
		Holding holding = awaiting(holdingId);
		if (actorId.equals(holding.getTaskAssigneeId())) {
			return awaitingView(holding);
		}
		if (holding.getTaskAssigneeId() != null) {
			throw new BusinessException(ErrorCode.CONFLICT, "Another finance member is already assigning this container");
		}
		holding.takeTask(actorId, now);
		holdings.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_TASK_ASSIGNED, ENTITY, holdingId));
		return awaitingView(holding);
	}

	/** The member holding the task gives it back to the queue, for another finance member to take. */
	@Transactional
	public AwaitingContainer releaseContainerTask(UUID actorId, UUID holdingId) {
		Holding holding = awaiting(holdingId);
		requireTaskOf(actorId, holding);
		holding.releaseTask();
		holdings.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_TASK_UNASSIGNED, ENTITY, holdingId));
		return awaitingView(holding);
	}

	private Holding awaiting(UUID holdingId) {
		Holding holding = holdings.findByIdForUpdate(holdingId)
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
		if (holding.getStatus() != HoldingStatus.AWAITING_CONTAINER) {
			throw new BusinessException(ErrorCode.CONFLICT, "This investment already has a container");
		}
		return holding;
	}

	private static void requireTaskOf(UUID actorId, Holding holding) {
		if (!actorId.equals(holding.getTaskAssigneeId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, holding.getTaskAssigneeId() == null
					? "Assign this task to yourself before assigning the container"
					: "Another finance member is assigning this container");
		}
	}

	private AwaitingContainer awaitingView(Holding h) {
		return awaitingView(h, offerings.terms(h.getProductId()));
	}

	private static AwaitingContainer awaitingView(Holding h, OfferingTerms plan) {
		return new AwaitingContainer(h.getId(), h.getUserId(), h.getOrderId(), h.getProductId(), plan.code(),
				plan.terms().title(), plan.terms().containerType(), h.amount(), h.getTenureMonths(), h.getConfirmedAt(),
				h.getTaskAssigneeId(), h.getTaskAssignedAt());
	}

	/** Paid investments awaiting a container, oldest first. */
	@Transactional(readOnly = true)
	public Page<AwaitingContainer> awaitingContainer(Pageable pageable) {
		Page<Holding> page = holdings.findByStatusOrderByConfirmedAtAscIdAsc(HoldingStatus.AWAITING_CONTAINER, pageable);
		Map<UUID, OfferingTerms> plans = offerings.terms(page.stream().map(Holding::getProductId).distinct().toList());
		return page.map(h -> awaitingView(h, plans.get(h.getProductId())));
	}

	@Transactional(readOnly = true)
	public long awaitingContainerCount() {
		return holdings.countByStatus(HoldingStatus.AWAITING_CONTAINER);
	}

	/** A paid investment awaiting its container, and the finance member who took the task (if anyone has). */
	public record AwaitingContainer(UUID holdingId, UUID userId, UUID orderId, UUID productId, String productCode,
			String productTitle, ContainerType containerType, Money amount, int tenureMonths, Instant paidAt,
			UUID taskAssigneeId, Instant taskAssignedAt) {
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

	/** One holding with its plan and container, e.g. for the lease agreement issued when its container is assigned. */
	@Transactional(readOnly = true)
	public HoldingResponse get(UUID holdingId) {
		Holding holding = holdings.findById(holdingId).orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
		return summarise(List.of(holding)).holdings().getFirst();
	}

	/** The investor a holding belongs to. */
	@Transactional(readOnly = true)
	public UUID ownerOf(UUID holdingId) {
		return holdings.findById(holdingId).map(Holding::getUserId)
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
	}

	/** For support tickets: a label for one of the user's own holdings, e.g. "LST-10001 · SLSU1234565". */
	@Transactional(readOnly = true)
	public Optional<String> ownedLabel(UUID userId, UUID holdingId) {
		return holdings.findById(holdingId)
			.filter(h -> h.getUserId().equals(userId))
			.map(h -> offerings.terms(List.of(h.getProductId())).get(h.getProductId()).code() + " · "
					+ (h.getContainerId() == null ? "awaiting container" : containers.summary(h.getContainerId()).containerNumber()));
	}

	@Transactional(readOnly = true)
	public Map<UUID, HoldingLabel> labels(Collection<UUID> holdingIds) {
		List<Holding> found = holdings.findAllById(holdingIds);
		Map<UUID, OfferingTerms> plans = offerings.terms(found.stream().map(Holding::getProductId).distinct().toList());
		Map<UUID, ContainerSummary> boxes = containers.summaries(found.stream().map(Holding::getContainerId).toList());
		Map<UUID, HoldingLabel> labels = new HashMap<>();
		for (Holding h : found) {
			OfferingTerms plan = plans.get(h.getProductId());
			ContainerSummary box = h.getContainerId() == null ? null : boxes.get(h.getContainerId());
			labels.put(h.getId(), new HoldingLabel(h.getId(), plan.code(), plan.terms().title(),
					box == null ? null : box.containerNumber(), h.getTenureMonths(), h.amount(), h.isPaidAtOffice()));
		}
		return labels;
	}

	/** The container assigned to each order line's holding, for lines that have one. */
	@Transactional(readOnly = true)
	public Map<UUID, String> containerNumbersByOrderItem(Collection<UUID> orderItemIds) {
		if (orderItemIds.isEmpty()) {
			return Map.of();
		}
		List<Holding> found = holdings.findByOrderItemIdIn(orderItemIds);
		Map<UUID, ContainerSummary> boxes = containers.summaries(found.stream().map(Holding::getContainerId).toList());
		Map<UUID, String> numbers = new HashMap<>();
		for (Holding h : found) {
			ContainerSummary box = h.getContainerId() == null ? null : boxes.get(h.getContainerId());
			if (box != null) {
				numbers.put(h.getOrderItemId(), box.containerNumber());
			}
		}
		return numbers;
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

	/** Capital confirmed into containers in one currency per UTC month since {@code from}; empty months are left out. */
	@Transactional(readOnly = true)
	public Map<YearMonth, Money> investedByMonth(Currency currency, Instant from) {
		Map<YearMonth, Money> totals = new TreeMap<>();
		for (Object[] row : holdings.sumAmountByMonth(currency.getCurrencyCode(), from)) {
			totals.put(YearMonth.parse((String) row[0]), Money.of((BigDecimal) row[1], currency));
		}
		return totals;
	}

	private PayoutTerms payoutTerms(Holding h, Instant leaseStartsAt) {
		OfferingTerms plan = offerings.terms(List.of(h.getProductId())).get(h.getProductId());
		return new PayoutTerms(h.getId(), h.getUserId(), h.getProductId(), plan.code(),
				containers.summary(h.getContainerId()).containerNumber(), h.amount(), h.monthlyRent(), h.capitalReturned(),
				h.getTenureMonths(), leaseStartsAt);
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
