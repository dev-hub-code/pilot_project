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

	public CartService(CartItemRepository items, OfferingQuery offerings) {
		this.items = items;
		this.offerings = offerings;
	}

	@Transactional(readOnly = true)
	public CartResponse view(UUID userId) {
		List<CartItem> lines = items.findByUserIdOrderByCreatedAt(userId);
		Map<UUID, OfferingTerms> terms = offerings.terms(lines.stream().map(CartItem::getProductId).toList());
		List<CartLineResponse> responses = new ArrayList<>();
		Money total = null;
		boolean ready = !lines.isEmpty();
		for (CartItem line : lines) {
			OfferingTerms plan = terms.get(line.getProductId());
			CartLineResponse response = describe(userId, line, plan);
			ready &= response.problems().isEmpty();
			if (plan != null) {
				Money amount = amount(plan, line.getQuantity());
				total = total == null ? amount : total.plus(amount);
			}
			responses.add(response);
		}
		return new CartResponse(responses, total == null ? null : MoneyResponse.from(total), ready);
	}

	/** Adds the plan to the cart, or changes how many containers if it is already there. */
	@Transactional
	public CartResponse setItem(UUID userId, UUID productId, int quantity) {
		OfferingTerms plan = offerings.terms(productId);
		List<String> problems = offerings.problems(userId, productId, quantity);
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, String.join("; ", problems));
		}
		CartItem existing = items.findByUserIdAndProductId(userId, productId).orElse(null);
		if (existing != null) {
			existing.changeQuantity(quantity);
		}
		else {
			List<CartItem> current = items.findByUserIdOrderByCreatedAt(userId);
			if (current.size() >= MAX_ITEMS) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A cart holds at most " + MAX_ITEMS + " plans");
			}
			Map<UUID, OfferingTerms> others = offerings.terms(current.stream().map(CartItem::getProductId).toList());
			if (others.values().stream().anyMatch(o -> !o.terms().currency().equals(plan.terms().currency()))) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"Your cart holds plans in another currency; check those out first");
			}
			items.save(new CartItem(userId, productId, quantity));
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
			.map(i -> new CartLine(i.getProductId(), i.getQuantity()))
			.toList();
	}

	/** Empties the cart once its lines became an order (same transaction). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void clear(UUID userId) {
		items.deleteAllOfUser(userId);
	}

	private CartLineResponse describe(UUID userId, CartItem line, OfferingTerms plan) {
		if (plan == null || !plan.status().isListed()) {
			return new CartLineResponse(line.getProductId(), null, "Unavailable plan", null, null, line.getQuantity(),
					null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, 0, null,
					List.of("This plan is no longer available; remove it"));
		}
		ProductTerms t = plan.terms();
		BigDecimal containers = BigDecimal.valueOf(line.getQuantity());
		return new CartLineResponse(plan.id(), plan.code(), t.title(), t.containerType(), plan.status(),
				line.getQuantity(), MoneyResponse.from(t.pricePerContainer()), MoneyResponse.from(amount(plan, line.getQuantity())),
				t.monthlyRentPercent(), t.monthlyCapitalReturnPercent(), MoneyResponse.from(t.monthlyPayout().times(containers)),
				t.tenureMonths(), MoneyResponse.from(t.totalPayout().times(containers)),
				offerings.problems(userId, plan.id(), line.getQuantity()));
	}

	private static Money amount(OfferingTerms plan, int quantity) {
		return plan.terms().pricePerContainer().times(BigDecimal.valueOf(quantity));
	}

}
