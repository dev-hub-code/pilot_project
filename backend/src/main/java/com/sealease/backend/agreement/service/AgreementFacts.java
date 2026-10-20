package com.sealease.backend.agreement.service;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The facts of one investment that its lease agreement states, gathered by whoever assigns the container. The
 * investor's own details (name, address) and the company's are looked up when the agreement is issued.
 *
 * @param containerLocation where the container is, or {@code null} when not recorded
 * @param leaseStartsOn     the lease's first day, or {@code null} while it waits for the investor to accept the
 *                          agreement (the agreement then requires acceptance, and states the dates once accepted)
 * @param leaseEndsOn       the day after the lease's last day (exclusive), as holdings record it; {@code null} with
 *                          {@code leaseStartsOn}
 */
public record AgreementFacts(UUID holdingId, UUID orderId, String orderNumber, UUID userId, String planCode,
		String planTitle, String containerNumber, String containerType, String containerLocation, Money purchasePrice,
		BigDecimal monthlyRentPercent, Money monthlyRent, int tenureMonths, LocalDate leaseStartsOn, LocalDate leaseEndsOn) {

	public AgreementFacts {
		Objects.requireNonNull(holdingId, "holdingId");
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(containerNumber, "containerNumber");
		if ((leaseStartsOn == null) != (leaseEndsOn == null)) {
			throw new IllegalArgumentException("A lease has both dates or neither");
		}
	}

	/** The lease starts only once the investor accepts the agreement. */
	public boolean awaitsAcceptance() {
		return leaseStartsOn == null;
	}

}
