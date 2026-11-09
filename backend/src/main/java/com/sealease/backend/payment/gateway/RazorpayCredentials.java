package com.sealease.backend.payment.gateway;

import com.sealease.backend.common.crypto.FieldEncryptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * The Razorpay credentials in force: those an admin saved (encrypted in {@code payment_gateway_settings}) over the
 * server's ({@code app.payments.razorpay.*}). Read on every use, not cached, so a change applies at once on every
 * instance. The key id and its secret always come from the same place.
 */
@Component
public class RazorpayCredentials {

	/** Encryption contexts: a ciphertext copied into the other column does not decrypt. */
	public static final String KEY_SECRET_CONTEXT = "payment_gateway.key_secret";
	public static final String WEBHOOK_SECRET_CONTEXT = "payment_gateway.webhook_secret";

	private final JdbcTemplate jdbc;
	private final FieldEncryptor encryptor;
	private final RazorpayProperties properties;

	public RazorpayCredentials(JdbcTemplate jdbc, FieldEncryptor encryptor, RazorpayProperties properties) {
		this.jdbc = jdbc;
		this.encryptor = encryptor;
		this.properties = properties;
	}

	public RazorpayKeys current() {
		List<RazorpayKeys> keys = jdbc.query("""
				SELECT key_id, key_secret_encrypted, webhook_secret_encrypted, merchant_name
				FROM payment_gateway_settings WHERE id = 1""", (rs, i) -> from(rs));
		return keys.isEmpty() ? server() : keys.getFirst();
	}

	/** From a {@code payment_gateway_settings} row (it must have the key columns). */
	public RazorpayKeys from(ResultSet rs) throws SQLException {
		String keyId = rs.getString("key_id");
		String keySecret = rs.getString("key_secret_encrypted");
		String webhook = rs.getString("webhook_secret_encrypted");
		String merchant = rs.getString("merchant_name");
		boolean adminKeys = keyId != null && keySecret != null;
		return new RazorpayKeys(
				adminKeys ? keyId : properties.keyId(),
				adminKeys ? encryptor.decrypt(keySecret, KEY_SECRET_CONTEXT) : properties.keySecret(),
				webhook != null ? encryptor.decrypt(webhook, WEBHOOK_SECRET_CONTEXT) : properties.webhookSecret(),
				merchant != null && !merchant.isBlank() ? merchant : properties.merchantName(),
				adminKeys, webhook != null);
	}

	/** The server's own configuration. */
	public RazorpayKeys server() {
		return new RazorpayKeys(properties.keyId(), properties.keySecret(), properties.webhookSecret(),
				properties.merchantName(), false, false);
	}

}
