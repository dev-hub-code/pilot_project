package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
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
 * Confirmed investments, their terms and the investor's portfolio view. A paid investment awaits the investor's
 * acceptance of its agreement; its term and payouts start when they accept.
 */
@Service
public class HoldingService {

	private static final String ENTITY = "HOLDING";

	private final HoldingRepository holdings;
	private final OfferingQuery offerings;
	private final AuditService audit;

	public HoldingService(HoldingRepository holdings, OfferingQuery offerings, AuditService audit) {
		this.holdings = holdings;
		this.offerings = offerings;
		this.audit = audit;
	}

	/** Called by order confirmation, in its transaction: a paid investment waiting for the investor's acceptance. */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID recordAwaitingAcceptance(UUID userId, UUID productId, UUID orderId, UUID orderItemId, Money amount,
			BigDecimal monthlyRentPercent, BigDecimal monthlyCapitalReturnPercent, int tenureMonths, Instant confirmedAt,
			boolean paidAtOffice) {
		return holdings.saveAndFlush(new Holding(userId, productId, orderId, orderItemId, amount, monthlyRentPercent,
				monthlyCapitalReturnPercent, tenureMonths, confirmedAt, paidAtOffice)).getId();
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
			throw new BusinessException(ErrorCode.CONFLICT, "This investment agreement has already been accepted");
		}
		holding.startLease(LocalDate.ofInstant(leaseStartsAt, ZoneOffset.UTC));
		holdings.flush();
		return payoutTerms(holding, leaseStartsAt);
	}

	/** The last payout has been paid: the holding matures. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void mature(UUID holdingId, Instant now) {
		Holding holding = holdings.findByIdForUpdate(holdingId)
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
		if (holding.getStatus() != HoldingStatus.ACTIVE) {
			return;
		}
		holding.mature(now);
		holdings.flush();
		audit.record(AuditRecord.of(null, AuditAction.HOLDING_MATURED, ENTITY, holdingId)
			.withNewValue(Map.of("leaseEndsOn", holding.getLeaseEndsOn().toString())));
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
			.map(h -> offerings.terms(List.of(h.getProductId())).get(h.getProductId()).code() + " · " + h.amount().display());
	}

	@Transactional(readOnly = true)
	public Map<UUID, HoldingLabel> labels(Collection<UUID> holdingIds) {
		List<Holding> found = holdings.findAllById(holdingIds);
		Map<UUID, OfferingTerms> plans = offerings.terms(found.stream().map(Holding::getProductId).distinct().toList());
		Map<UUID, HoldingLabel> labels = new HashMap<>();
		for (Holding h : found) {
			OfferingTerms plan = plans.get(h.getProductId());
			labels.put(h.getId(), new HoldingLabel(h.getId(), plan.code(), plan.terms().title(), h.getTenureMonths(), h.amount(), h.isPaidAtOffice()));
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

	/** Capital confirmed into containers in one currency per UTC month since {@code from}; empty months are left out. */
	@Transactional(readOnly = true)
	public Map<YearMonth, Money> investedByMonth(Currency currency, Instant from) {
		Map<YearMonth, Money> totals = new TreeMap<>();
		for (Object[] row : holdings.sumAmountByMonth(currency.getCurrencyCode(), from)) {
			totals.put(YearMonth.parse((String) row[0]), Money.of((BigDecimal) row[1], currency));
		}
		return totals;
	}

	/** Whether the holding belongs to the investor's first confirmed order. */
	@Transactional(readOnly = true)
	public boolean isOfFirstOrder(UUID userId, UUID holdingId) {
		UUID first = holdings.findFirstByUserIdOrderByConfirmedAtAscOrderIdAsc(userId).map(Holding::getOrderId).orElse(null);
		return first != null && holdings.findById(holdingId).map(h -> first.equals(h.getOrderId())).orElse(false);
	}

	/** The order a holding was bought in. */
	@Transactional(readOnly = true)
	public UUID orderOf(UUID holdingId) {
		return holdings.findById(holdingId).map(Holding::getOrderId)
			.orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
	}

	private PayoutTerms payoutTerms(Holding h, Instant leaseStartsAt) {
		OfferingTerms plan = offerings.terms(List.of(h.getProductId())).get(h.getProductId());
		return new PayoutTerms(h.getId(), h.getUserId(), h.getProductId(), plan.code(), h.amount(), h.monthlyRent(), h.capitalReturned(),
				h.getTenureMonths(), leaseStartsAt);
	}

	private PortfolioResponse summarise(List<Holding> owned) {
		Map<UUID, OfferingTerms> plans = offerings.terms(owned.stream().map(Holding::getProductId).distinct().toList());
		Map<String, Money> totals = new TreeMap<>();
		int active = 0;
		for (Holding h : owned) {
			totals.merge(h.amount().currency().getCurrencyCode(), h.amount(), Money::plus);
			active += h.getStatus() == HoldingStatus.ACTIVE ? 1 : 0;
		}
		return new PortfolioResponse(totals.values().stream().map(MoneyResponse::from).toList(), active,
				owned.stream()
					.map(h -> HoldingResponse.from(h, plans.get(h.getProductId())))
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
