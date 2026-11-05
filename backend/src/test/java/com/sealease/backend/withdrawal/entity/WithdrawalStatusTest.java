package com.sealease.backend.withdrawal.entity;

import org.junit.jupiter.api.Test;

import static com.sealease.backend.withdrawal.entity.WithdrawalStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalStatusTest {

	@Test
	void reviewComesBeforeApprovalAndPayment() {
		assertThat(SUBMITTED.canMoveTo(UNDER_REVIEW)).isTrue();
		assertThat(SUBMITTED.canMoveTo(APPROVED)).isFalse();
		assertThat(SUBMITTED.canMoveTo(PAID)).isFalse();
		assertThat(UNDER_REVIEW.canMoveTo(PENDING_APPROVAL)).isTrue();
		assertThat(ON_HOLD.canMoveTo(APPROVED)).isFalse();
		assertThat(ON_HOLD.canMoveTo(UNDER_REVIEW)).isTrue();
	}

	@Test
	void paidOnlyFromThePaymentStagesOrAnOfficeHandOver() {
		for (WithdrawalStatus s : values()) {
			if (s.canMoveTo(PAID)) {
				assertThat(s).isIn(APPROVED, PROCESSING, FAILED);
			}
		}
		assertThat(BATCHED.canMoveTo(PAID)).isFalse();
	}

	@Test
	void finalStatusesGoNowhereAndHoldNoMoney() {
		for (WithdrawalStatus s : new WithdrawalStatus[] { PAID, RETURNED, REJECTED, CANCELLED }) {
			assertThat(s.isFinal()).isTrue();
			assertThat(s.isOpen()).isFalse();
			for (WithdrawalStatus next : values()) {
				assertThat(s.canMoveTo(next)).isFalse();
			}
		}
	}

	@Test
	void theInvestorCanCancelOnlyWhileUnderReview() {
		for (WithdrawalStatus s : IN_REVIEW) {
			assertThat(s.canMoveTo(CANCELLED)).isTrue();
		}
		assertThat(BATCHED.canMoveTo(CANCELLED)).isFalse();
		assertThat(PROCESSING.canMoveTo(CANCELLED)).isFalse();
	}

}
