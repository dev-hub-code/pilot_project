package com.sealease.backend.investment.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.dto.CapacityView;
import com.sealease.backend.investment.entity.CapacityMovement;
import com.sealease.backend.investment.entity.CapacityMovementType;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.repository.CapacityMovementRepository;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The only way investment capacity changes (Rule 14: capacity cannot be oversold).
 *
 * <ul>
 * <li>Every change locks the offering row ({@code SELECT … FOR UPDATE}), so concurrent investors
 * are serialised and each sees the true availability; the database CHECK
 * {@code committed + reserved <= total} is the final backstop.</li>
 * <li>Every change is an immutable {@link CapacityMovement} keyed by a caller-supplied reference
 * (cart item / order id). Repeating a call with the same reference is a no-op (Rule 12), and a
 * reservation can be settled — released or committed — exactly once.</li>
 * </ul>
 */
@Service
public class CapacityService {

	private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9:_-]{1,100}");

	private final InvestmentProductRepository products;
	private final CapacityMovementRepository movements;
	private final EligibilityService eligibility;
	private final Clock clock;

	public CapacityService(InvestmentProductRepository products, CapacityMovementRepository movements,
			EligibilityService eligibility, Clock clock) {
		this.products = products;
		this.movements = movements;
		this.eligibility = eligibility;
		this.clock = clock;
	}

	@Transactional
	public CapacityView reserve(UUID productId, UUID investorId, Money amount, String reference) {
		requireReference(reference);
		InvestmentProduct product = lock(productId);

		Optional<CapacityMovement> previous = movements.findByProductIdAndMovementTypeAndReference(productId,
				CapacityMovementType.RESERVE, reference);
		if (previous.isPresent()) {
			CapacityMovement existing = previous.get();
			if (existing.getInvestorUserId().equals(investorId) && existing.getAmount().compareTo(amount.amount()) == 0) {
				return CapacityView.of(product);
			}
			throw new BusinessException(ErrorCode.CONFLICT, "Reference " + reference + " was already used differently");
		}

		InvestorEligibility verdict = eligibility.evaluate(investorId, product);
		if (!verdict.eligible()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, String.join("; ", verdict.reasons()));
		}
		Money exposure = Money.of(movements.exposureOf(productId, investorId), product.currencyUnit());
		List<String> violations = InvestmentAmountPolicy.violations(product.terms(), amount, product.available(), exposure);
		if (!violations.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, String.join("; ", violations));
		}

		product.applyReservation(amount.amount());
		record(product, CapacityMovementType.RESERVE, amount, reference, investorId);
		return CapacityView.of(product);
	}

	@Transactional
	public CapacityView release(UUID productId, String reference) {
		return settle(productId, reference, CapacityMovementType.RELEASE);
	}

	@Transactional
	public CapacityView commit(UUID productId, String reference) {
		return settle(productId, reference, CapacityMovementType.COMMIT);
	}

	@Transactional(readOnly = true)
	public CapacityView snapshot(UUID productId) {
		return CapacityView.of(products.findById(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Investment product", productId)));
	}

	private CapacityView settle(UUID productId, String reference, CapacityMovementType settlement) {
		requireReference(reference);
		InvestmentProduct product = lock(productId);
		List<CapacityMovement> history = movements.findByProductIdAndReference(productId, reference);
		CapacityMovement reservation = history.stream()
			.filter(m -> m.getMovementType() == CapacityMovementType.RESERVE)
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Reservation", reference));
		Optional<CapacityMovement> settled = history.stream()
			.filter(m -> m.getMovementType() != CapacityMovementType.RESERVE)
			.findFirst();
		if (settled.isPresent()) {
			if (settled.get().getMovementType() == settlement) {
				return CapacityView.of(product);
			}
			throw new BusinessException(ErrorCode.CONFLICT,
					"Reservation " + reference + " was already " + settled.get().getMovementType());
		}

		Money amount = Money.of(reservation.getAmount(), product.currencyUnit());
		if (settlement == CapacityMovementType.COMMIT) {
			product.applyCommit(amount.amount());
		}
		else {
			product.applyRelease(amount.amount());
		}
		record(product, settlement, amount, reference, reservation.getInvestorUserId());
		return CapacityView.of(product);
	}

	private void record(InvestmentProduct product, CapacityMovementType type, Money amount, String reference,
			UUID investorId) {
		products.flush();
		movements.save(new CapacityMovement(product.getId(), type, amount.amount(), reference, investorId,
				product.reserved().amount(), product.committed().amount(), clock.instant()));
		movements.flush();
	}

	private InvestmentProduct lock(UUID productId) {
		return products.findByIdForUpdate(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Investment product", productId));
	}

	private static void requireReference(String reference) {
		if (reference == null || !REFERENCE.matcher(reference).matches()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid capacity reference");
		}
	}

}
