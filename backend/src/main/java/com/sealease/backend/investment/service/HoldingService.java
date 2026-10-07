package com.sealease.backend.investment.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.dto.PortfolioResponse;
import com.sealease.backend.investment.entity.Holding;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.investment.repository.HoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Confirmed investments (holdings) and the investor's portfolio view. */
@Service
public class HoldingService {

	private final HoldingRepository holdings;
	private final OfferingQuery offerings;

	public HoldingService(HoldingRepository holdings, OfferingQuery offerings) {
		this.holdings = holdings;
		this.offerings = offerings;
	}

	/** Called by order confirmation, in its transaction, after the capacity was committed. */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID record(UUID userId, UUID productId, UUID orderId, UUID orderItemId, Money amount,
			BigDecimal ownershipPercent, String termsVersion, Instant confirmedAt) {
		Holding holding = holdings.save(new Holding(userId, productId, orderId, orderItemId, amount, ownershipPercent,
				termsVersion, confirmedAt));
		return holding.getId();
	}

	@Transactional(readOnly = true)
	public PortfolioResponse portfolio(UUID userId) {
		List<Holding> owned = holdings.findByUserIdOrderByConfirmedAtDesc(userId);
		return summarise(owned);
	}

	/** For support tickets: a label for one of the user's own holdings, e.g. "CONT-10001 · 5000.00 USD". */
	@Transactional(readOnly = true)
	public Optional<String> ownedLabel(UUID userId, UUID holdingId) {
		return holdings.findById(holdingId)
			.filter(h -> h.getUserId().equals(userId))
			.map(h -> offerings.terms(List.of(h.getProductId())).get(h.getProductId()).code() + " · " + h.amount().display());
	}

	@Transactional(readOnly = true)
	public List<HoldingResponse> forOrder(UUID orderId) {
		return summarise(holdings.findByOrderIdOrderByCreatedAt(orderId)).holdings();
	}

	private PortfolioResponse summarise(List<Holding> owned) {
		Map<UUID, OfferingTerms> terms = offerings.terms(owned.stream().map(Holding::getProductId).distinct().toList());
		Map<String, Money> totals = new TreeMap<>();
		int active = 0;
		for (Holding h : owned) {
			totals.merge(h.amount().currency().getCurrencyCode(), h.amount(), Money::plus);
			active += h.getStatus() == HoldingStatus.ACTIVE ? 1 : 0;
		}
		return new PortfolioResponse(totals.values().stream().map(MoneyResponse::from).toList(), active,
				owned.stream().map(h -> HoldingResponse.from(h, terms.get(h.getProductId()))).toList());
	}

}
