package com.sealease.backend.earning.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.dto.HoldingShare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * How one rental receipt is divided. Pure and exact:
 *
 * <ul>
 * <li>gross share = receipt × holding amount ÷ price, rounded <em>down</em> to the currency's minor
 * unit, so the shares can never add up to more than was received;</li>
 * <li>fee = gross share × fee %, rounded half-up to the minor unit; net = gross − fee;</li>
 * <li>retained = receipt − Σ gross: the unsold share of the offering plus rounding remainders.</li>
 * </ul>
 *
 * Hence {@code received = Σ net + fees + retained} always holds to the last digit.
 */
public record RentalSplit(Money received, List<Share> shares, Money fees, Money retained) {

	public record Share(UUID holdingId, UUID userId, BigDecimal ownershipPercent, Money gross, Money fee, Money net) {
	}

	public static RentalSplit of(Money received, Money price, BigDecimal feePercent, List<HoldingShare> holders) {
		Currency currency = received.currency();
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		BigDecimal feeRate = feePercent.movePointLeft(2);
		List<Share> shares = new ArrayList<>(holders.size());
		Money grossTotal = Money.zero(currency);
		Money fees = Money.zero(currency);
		for (HoldingShare holder : holders) {
			Money gross = Money.of(received.amount().multiply(holder.amount().amount())
				.divide(price.amount(), digits, RoundingMode.DOWN), currency);
			Money fee = Money.of(gross.amount().multiply(feeRate).setScale(digits, RoundingMode.HALF_UP), currency);
			shares.add(new Share(holder.holdingId(), holder.userId(), holder.ownershipPercent(), gross, fee,
					gross.minus(fee)));
			grossTotal = grossTotal.plus(gross);
			fees = fees.plus(fee);
		}
		Money retained = received.minus(grossTotal);
		if (retained.isNegative()) {
			throw new IllegalStateException("Holdings exceed the offering price: " + grossTotal + " > " + received);
		}
		return new RentalSplit(received, List.copyOf(shares), fees, retained);
	}

}
