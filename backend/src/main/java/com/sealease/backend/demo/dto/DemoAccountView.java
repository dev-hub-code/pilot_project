package com.sealease.backend.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.investment.dto.RentTier;

/**
 * A demo account with its dummy orders and downline, and the figures worked out from them.
 *
 * @param rates          commission percent per level 1-4, the rates in force
 * @param creditedUntil  earnings are credited up to (not including) this day
 * @param months         every month of the time frame, oldest first
 * @param payouts        made so far, oldest first: rent on the 1st, commission on the 5th
 * @param plans          the plans an order can be of
 * @param rentBands      monthly rent by order total, lowest first: an order earns its band's rate
 */
public record DemoAccountView(UUID userId, String userCode, String name, String email, LocalDate startsOn,
		LocalDate endsOn, LocalDate today, LocalDate creditedUntil, List<BigDecimal> rates, List<Order> orders,
		List<Member> members, List<Month> months, List<Payout> payouts, Totals totals, List<Plan> plans,
		List<RentTier> rentBands) {

	/** A plan dummy orders can be of: open or closed. */
	public record Plan(UUID id, String code, String title, ContainerType containerType, MoneyResponse price,
			int minTenureYears, int maxTenureYears, String status) {
	}

	/**
	 * @param memberId   {@code null} for the investor's own order
	 * @param earned     rent credited so far (own order), or commission it earned the investor (member's)
	 */
	public record Order(UUID id, UUID memberId, String memberName, String orderNumber, UUID productId, String planCode,
			String planTitle,
			ContainerType containerType, int containers, MoneyResponse pricePerContainer, MoneyResponse amount,
			BigDecimal rentPercent, MoneyResponse monthlyRent, int tenureMonths, LocalDate startsOn, LocalDate endsOn,
			MoneyResponse earned) {
	}

	/** @param commission credited to the investor so far from this member's orders */
	public record Member(UUID id, UUID parentId, String name, LocalDate joinedOn, int level, MoneyResponse commission) {
	}

	/** @param month {@code yyyy-MM} */
	public record Month(String month, MoneyResponse rentPaid, MoneyResponse rentScheduled, MoneyResponse commission,
			MoneyResponse commissionScheduled) {
	}

	/**
	 * @param kind     RENT or REFERRAL
	 * @param earnedIn {@code yyyy-MM}
	 */
	public record Payout(String kind, String earnedIn, LocalDate paidOn, MoneyResponse amount) {
	}

	public record Totals(MoneyResponse rentPaid, MoneyResponse commissionPaid, MoneyResponse rentWithdrawn,
			MoneyResponse referralWithdrawn, MoneyResponse rentBalance, MoneyResponse referralBalance) {
	}

}
