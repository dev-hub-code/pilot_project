package com.sealease.backend.withdrawal.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The withdrawal lifecycle. Stored by name, so constants must never be renamed once released.
 *
 * <p>An online investor's request: SUBMITTED → UNDER_REVIEW (a reviewer takes it) → APPROVED, or PENDING_APPROVAL
 * first when it needs a second, different approver. Review may put it ON_HOLD (with a reason, possibly asking the
 * investor for more information) and resume it, or REJECT it with a reason. The investor may CANCEL it until it is
 * approved.
 *
 * <p>Payment: APPROVED → BATCHED (payment initiated: handed to the payout provider) → PROCESSING (with the bank,
 * awaiting confirmation) → PAID, or FAILED. A failed payment is held until finance resolves it: PAID (paid by hand,
 * with the bank reference) or RETURNED (back to the investor's wallet). Withdrawal runs and office withdrawals start
 * at APPROVED.
 *
 * <p>While open, the amount is held in "withdrawals in transit"; RETURNED, REJECTED and CANCELLED give it back.
 */
public enum WithdrawalStatus {
	SUBMITTED,
	UNDER_REVIEW,
	ON_HOLD,
	PENDING_APPROVAL,
	APPROVED,
	BATCHED,
	PROCESSING,
	PAID,
	FAILED,
	RETURNED,
	REJECTED,
	CANCELLED;

	/** Money is held for these: not yet paid out or given back. */
	public static final Set<WithdrawalStatus> OPEN = EnumSet.of(SUBMITTED, UNDER_REVIEW, ON_HOLD, PENDING_APPROVAL,
			APPROVED, BATCHED, PROCESSING, FAILED);

	/** Still being decided: the investor may cancel, review may reject or hold. */
	public static final Set<WithdrawalStatus> IN_REVIEW = EnumSet.of(SUBMITTED, UNDER_REVIEW, ON_HOLD, PENDING_APPROVAL);

	/** With the payout provider: payment initiated and not yet confirmed either way. */
	public static final Set<WithdrawalStatus> IN_PAYMENT = EnumSet.of(BATCHED, PROCESSING);

	private static final Map<WithdrawalStatus, Set<WithdrawalStatus>> NEXT = new EnumMap<>(WithdrawalStatus.class);

	static {
		NEXT.put(SUBMITTED, EnumSet.of(UNDER_REVIEW, ON_HOLD, REJECTED, CANCELLED));
		NEXT.put(UNDER_REVIEW, EnumSet.of(PENDING_APPROVAL, APPROVED, ON_HOLD, REJECTED, CANCELLED));
		NEXT.put(PENDING_APPROVAL, EnumSet.of(APPROVED, ON_HOLD, REJECTED, CANCELLED));
		NEXT.put(ON_HOLD, EnumSet.of(UNDER_REVIEW, REJECTED, CANCELLED));
		// Office withdrawals are handed over (PAID) straight from APPROVED; earlier ones could be rejected or cancelled.
		NEXT.put(APPROVED, EnumSet.of(BATCHED, PAID, REJECTED, CANCELLED));
		NEXT.put(BATCHED, EnumSet.of(PROCESSING, APPROVED));
		NEXT.put(PROCESSING, EnumSet.of(PAID, FAILED));
		NEXT.put(FAILED, EnumSet.of(PAID, RETURNED));
		NEXT.put(PAID, EnumSet.noneOf(WithdrawalStatus.class));
		NEXT.put(RETURNED, EnumSet.noneOf(WithdrawalStatus.class));
		NEXT.put(REJECTED, EnumSet.noneOf(WithdrawalStatus.class));
		NEXT.put(CANCELLED, EnumSet.noneOf(WithdrawalStatus.class));
	}

	public boolean isOpen() {
		return OPEN.contains(this);
	}

	public boolean isFinal() {
		return NEXT.get(this).isEmpty();
	}

	/** Whether the lifecycle allows going from this status straight to {@code next}. */
	public boolean canMoveTo(WithdrawalStatus next) {
		return NEXT.get(this).contains(next);
	}

}
