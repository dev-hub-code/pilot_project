package com.sealease.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bank transfer into a per-order Razorpay virtual account is no longer offered, but accounts opened before then may still
 * take money. This records one such pending payment directly, as it was stored.
 */
public final class OldVirtualAccounts {

	private OldVirtualAccounts() {
	}

	/** A pending virtual-account payment of the order's amount (in paise); returns Razorpay's id for the account. */
	public static String open(JdbcTemplate jdbc, UUID userId, String orderId, long paise) {
		String account = "va_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
		jdbc.update("""
				INSERT INTO payments (id, order_id, user_id, method, provider, provider_reference, amount, currency, status,
				                      idempotency_key, created_at, updated_at, virtual_account_number, virtual_account_ifsc,
				                      virtual_account_name, virtual_account_bank)
				VALUES (?, ?::uuid, ?, 'VIRTUAL_ACCOUNT', 'RAZORPAY', ?, ?, 'INR', 'PENDING', ?, now(), now(),
				        '2223330012345678', 'RATN0VAAPIS', 'SeaLease', 'RBL Bank')""",
				UUID.randomUUID(), orderId, userId, account, BigDecimal.valueOf(paise, 2), UUID.randomUUID().toString());
		return account;
	}

}
