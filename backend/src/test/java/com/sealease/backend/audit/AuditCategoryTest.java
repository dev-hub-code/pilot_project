package com.sealease.backend.audit;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCategoryTest {

	@Test
	void everyActionIsInExactlyOneCategory() {
		for (AuditAction action : AuditAction.values()) {
			long categories = Arrays.stream(AuditCategory.values()).filter(c -> c.actions().contains(action)).count();
			assertThat(categories).as(action.name()).isEqualTo(1);
		}
	}

	@Test
	void unlistedActionsAreOperations() {
		assertThat(AuditCategory.of(AuditAction.PRODUCT_PUBLISHED)).isEqualTo(AuditCategory.OPERATIONS);
		assertThat(AuditCategory.of(AuditAction.LOGIN_FAILED)).isEqualTo(AuditCategory.SIGN_IN);
		assertThat(AuditCategory.of(AuditAction.KYC_DOCUMENT_VIEWED)).isEqualTo(AuditCategory.SENSITIVE_DATA);
	}

	@Test
	void callersGetACopy() {
		AuditCategory.SIGN_IN.actions().clear();
		assertThat(AuditCategory.SIGN_IN.actions()).contains(AuditAction.LOGIN);
	}

}
