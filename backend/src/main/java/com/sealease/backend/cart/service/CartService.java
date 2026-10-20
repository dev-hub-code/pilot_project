package com.sealease.backend.cart.service;

import com.sealease.backend.cart.dto.CartLineResponse;
import com.sealease.backend.cart.dto.CartResponse;
import com.sealease.backend.cart.entity.CartItem;
import com.sealease.backend.cart.repository.CartItemRepository;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.service.OfferingQuery;
import com.sealease.backend.investment.service.RentRateService;
import com.sealease.backend.order.service.MinimumOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The investor's basket: how many containers of which plans. It reserves nothing - containers can
 * sell out while they sit in a cart - but every read re-validates each line so the investor sees
 * problems before checking out. One currency per cart, because an order and its payment are in one
 * currency.
 */
@Service
public class CartService {

	static final int MAX_ITEMS = 20;

	private final CartItemRepository items;
	private final OfferingQuery offerings;
	private final RentRateService rentRates;
	private final MinimumOrderService minimumOrder;

	public CartService(CartItemRepository items, OfferingQuery offerings, RentRateService rentRates,
			MinimumOrderService minimumOrder) {
		this.items = items;
		this.offerings = offerings;
		this.rentRates = rentRates;
		this.minimumOrder = minimumOrder;
	}

	@Transactional(readOnly = true)
	public CartResponse view(UUID userId) {
		List<CartItem> lines = items.findByUserIdOrderByCreatedAt(userId);
		Map<UUID, OfferingTerms> terms = offerings.terms(lines.stream().map(CartItem::getProductId).toList());
		Money total = null;
		for (CartItem line : lines) {
			OfferingTerms plan = terms.get(line.getProductId());
			if (plan != null) {
				Money amount = amount(plan, line.getQuantity());
				total = total == null ? amount : total.plus(amount);
			}
		}
		// Every container in the order earns the rate for the order's total.
		BigDecimal rate = total == null ? null : rentRates.percentFor(total);
		List<CartLineResponse> responses = new ArrayList<>();
		boolean ready = !lines.isEmpty();
		for (CartItem line : lines) {
			CartLineResponse response = describe(userId, line, terms.get(line.getProductId()), rate);
			ready &= response.problems().isEmpty();
			responses.add(response);
		}
		List<String> problems = total == null ? List.of() : minimumOrder.problems(total);
		if (total != null && problems.isEmpty()) {
			problems = rentRates.problems(total);
		}
		return new CartResponse(responses, total == null ? null : MoneyResponse.from(total),
				MoneyResponse.from(minimumOrder.current()), problems, ready && problems.isEmpty());
	}

	/**
	 * Adds the plan to the cart, or changes how many containers and the lease if it is already there.
	 *
	 * @param tenureYears the lease in whole years; {@code null} keeps the line's lease, or takes the plan's shortest
	 */
	@Transactional
	public CartResponse setItem(UUID userId, UUID productId, int quantity, Integer tenureYears) {
		OfferingTerms plan = offerings.terms(productId);
		CartItem existing = items.findByUserIdAndProductId(userId, productId).orElse(null);
		int years = tenureYears != null ? tenureYears
				: existing != null ? existing.getTenureYears() : plan.terms().minTenureYears();
		List<String> problems = offerings.problems(userId, productId, quantity, years);
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, String.join("; ", problems));
		}
		if (existing != null) {
			existing.change(quantity, years);
		}
		else {
			List<CartItem> current = items.findByUserIdOrderByCreatedAt(userId);
			if (current.size() >= MAX_ITEMS) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A cart holds at most " + MAX_ITEMS + " container listings");
			}
			Map<UUID, OfferingTerms> others = offerings.terms(current.stream().map(CartItem::getProductId).toList());
			if (others.values().stream().anyMatch(o -> !o.terms().currency().equals(plan.terms().currency()))) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"Your cart holds containers in another currency; check those out first");
			}
			items.save(new CartItem(userId, productId, quantity, years));
		}
		items.flush();
		return view(userId);
	}

	@Transactional
	public CartResponse removeItem(UUID userId, UUID productId) {
		CartItem item = items.findByUserIdAndProductId(userId, productId)
			.orElseThrow(() -> new ResourceNotFoundException("Cart item", productId));
		items.delete(item);
		items.flush();
		return view(userId);
	}

	/** The lines to check out, locked until the checkout transaction ends. */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<CartLine> lockForCheckout(UUID userId) {
		return items.findByUserIdForUpdate(userId).stream()
			.map(i -> new CartLine(i.getProductId(), i.getQuantity(), i.getTenureYears()))
			.toList();
	}

	/** Empties the cart once its lines became an order (same transaction). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void clear(UUID userId) {
		items.deleteAllOfUser(userId);
	}

	private CartLineResponse describe(UUID userId, CartItem line, OfferingTerms plan, BigDecimal rate) {
		if (plan == null || !plan.status().isListed()) {
			return new CartLineResponse(line.getProductId(), null, "Unavailable container", null, null, line.getQuantity(),
					null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, line.getTenureYears(), 0, 0,
					null,
					List.of("This container is no longer available; remove it"));
		}
		ProductTerms t = plan.terms();
		BigDecimal containers = BigDecimal.valueOf(line.getQuantity());
		if (rate == null) {
			// Below the lowest rent band: no rent to show until the order is large enough (the cart says so).
			return new CartLineResponse(plan.id(), plan.code(), t.title(), t.containerType(), plan.status(),
					line.getQuantity(), MoneyResponse.from(t.pricePerContainer()), MoneyResponse.from(amount(plan, line.getQuantity())),
					BigDecimal.ZERO, t.monthlyCapitalReturnPercent(), null, line.getTenureYears(), t.minTenureYears(),
					t.maxTenureYears(), null, offerings.problems(userId, plan.id(), line.getQuantity(), line.getTenureYears()));
		}
		return new CartLineResponse(plan.id(), plan.code(), t.title(), t.containerType(), plan.status(),
				line.getQuantity(), MoneyResponse.from(t.pricePerContainer()), MoneyResponse.from(amount(plan, line.getQuantity())),
				rate, t.monthlyCapitalReturnPercent(), MoneyResponse.from(t.monthlyRent(rate).times(containers)),
				line.getTenureYears(), t.minTenureYears(), t.maxTenureYears(),
				MoneyResponse.from(t.totalPayout(line.getTenureYears(), rate).times(containers)),
				offerings.problems(userId, plan.id(), line.getQuantity(), line.getTenureYears()));
	}

	private static Money amount(OfferingTerms plan, int quantity) {
		return plan.terms().pricePerContainer().times(BigDecimal.valueOf(quantity));
	}

}
