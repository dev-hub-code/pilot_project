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
import com.sealease.backend.investment.service.InvestmentAmountPolicy;
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
 * The investor's basket. It reserves nothing - an offering can sell out while it sits in a cart -
 * but every read re-validates each line so the investor sees problems before checking out.
 * One currency per cart, because an order and its payment are in one currency.
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
			CartLineResponse response = describe(userId, line, terms.get(line.getProductId()));
			ready &= response.problems().isEmpty();
			total = total == null ? line.amount() : total.plus(line.amount());
			responses.add(response);
		}
		return new CartResponse(responses, total == null ? null : MoneyResponse.from(total), ready);
	}

	/** Adds the offering to the cart, or changes the amount if it is already there. */
	@Transactional
	public CartResponse setItem(UUID userId, UUID productId, BigDecimal requested) {
		OfferingTerms offering = offerings.terms(productId);
		Money amount = Money.of(requested, offering.terms().currency());
		List<String> problems = offerings.problems(userId, productId, amount);
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, String.join("; ", problems));
		}
		CartItem existing = items.findByUserIdAndProductId(userId, productId).orElse(null);
		if (existing != null) {
			existing.changeAmount(amount);
		}
		else {
			List<CartItem> current = items.findByUserIdOrderByCreatedAt(userId);
			if (current.size() >= MAX_ITEMS) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"A cart holds at most " + MAX_ITEMS + " offerings");
			}
			if (current.stream().anyMatch(i -> !i.getCurrency().equals(amount.currency().getCurrencyCode()))) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"Your cart holds offerings in another currency; check those out first");
			}
			items.save(new CartItem(userId, productId, amount));
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
			.map(i -> new CartLine(i.getProductId(), i.amount()))
			.toList();
	}

	/** Empties the cart once its lines became an order (same transaction). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void clear(UUID userId) {
		items.deleteAllOfUser(userId);
	}

	private CartLineResponse describe(UUID userId, CartItem line, OfferingTerms offering) {
		Money amount = line.amount();
		if (offering == null || !offering.status().isListed()) {
			return new CartLineResponse(line.getProductId(), null, "Unavailable offering", null, null,
					MoneyResponse.from(amount), BigDecimal.ZERO, null, null, 0, null,
					List.of("This offering is no longer available; remove it"));
		}
		ProductTerms t = offering.terms();
		return new CartLineResponse(offering.id(), offering.code(), t.title(), t.investmentType(), offering.status(),
				MoneyResponse.from(amount), InvestmentAmountPolicy.ownershipPercent(t, amount),
				MoneyResponse.from(InvestmentAmountPolicy.rentalShare(t, amount)), t.rentalFrequency(),
				t.durationMonths(), t.termsVersion(), offerings.problems(userId, offering.id(), amount));
	}

}
