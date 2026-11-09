package com.sealease.backend.payment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.payment.dto.PaymentGatewayKeysRequest;
import com.sealease.backend.payment.dto.PaymentGatewaySettingsRequest;
import com.sealease.backend.payment.dto.PaymentGatewaySettingsResponse;
import com.sealease.backend.payment.gateway.RazorpayCredentials;
import com.sealease.backend.payment.gateway.RazorpayKeys;
import com.sealease.backend.payment.gateway.RazorpayProperties;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How online payments through Razorpay run, set by an admin (one row, {@code payment_gateway_settings}): whether online
 * investors pay through Razorpay at all, the most one checkout payment can be, and how long an order is held while the
 * investor pays. Until an admin saves, the server's
 * configuration ({@code app.payments.razorpay.*}) applies. Admins may also set the API keys, webhook secret and merchant
 * name ({@link #updateKeys}); the secrets are stored encrypted and never shown again, only their last four characters.
 *
 * <p>Read on every payment decision, not cached, so a change applies at once on every instance. Switching online
 * payments off stops new ones only: payments already started still settle (checkout results and webhooks are honoured).
 */
@Service
public class PaymentGatewaySettingsService {

	private static final String ENTITY = "PAYMENT_GATEWAY_SETTINGS";

	private final JdbcTemplate jdbc;
	private final RazorpayProperties razorpay;
	private final MoneyProperties money;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final RazorpayCredentials credentials;
	private final FieldEncryptor encryptor;
	private final Clock clock;

	public PaymentGatewaySettingsService(JdbcTemplate jdbc, RazorpayProperties razorpay, MoneyProperties money,
			UserAccountService accounts, AuditService audit, RazorpayCredentials credentials, FieldEncryptor encryptor,
			Clock clock) {
		this.credentials = credentials;
		this.encryptor = encryptor;
		this.jdbc = jdbc;
		this.razorpay = razorpay;
		this.money = money;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/** The settings in force. */
	@Transactional(readOnly = true)
	public GatewaySettings current() {
		return jdbc.queryForObject("SELECT * FROM payment_gateway_settings WHERE id = 1", (rs, i) -> effective(rs));
	}

	@Transactional(readOnly = true)
	public PaymentGatewaySettingsResponse view() {
		return jdbc.queryForObject("SELECT * FROM payment_gateway_settings WHERE id = 1", (rs, i) -> {
			GatewaySettings now = effective(rs);
			RazorpayKeys keys = credentials.from(rs);
			Timestamp updatedAt = rs.getTimestamp("updated_at");
			Timestamp keysUpdatedAt = rs.getTimestamp("keys_updated_at");
			PaymentGatewaySettingsResponse.Keys keyView = new PaymentGatewaySettingsResponse.Keys(
					keys.enabled() ? keys.keyId() : null, hint(keys.keySecret()), hint(keys.webhookSecret()),
					keys.merchantName(), keys.keysFromAdmin() ? "ADMIN" : keys.enabled() ? "SERVER" : null,
					keys.webhookFromAdmin() ? "ADMIN" : keys.webhookEnabled() ? "SERVER" : null,
					keysUpdatedAt == null ? null : keysUpdatedAt.toInstant(),
					name(rs.getObject("keys_updated_by", UUID.class)));
			return new PaymentGatewaySettingsResponse(keys.enabled(), keys.mode(), keys.webhookEnabled(),
					now.onlinePayments(), rupees(now.maxAmount()), (int) now.checkoutWindow().toMinutes(),
					new PaymentGatewaySettingsResponse.Defaults(true, rupees(defaultMaxAmount()),
							(int) razorpay.checkoutWindow().toMinutes()),
					updatedAt == null ? null : updatedAt.toInstant(), name(rs.getObject("updated_by", UUID.class)), keyView);
		});
	}

	/**
	 * Sets the Razorpay API keys, webhook secret and merchant name. A secret left blank keeps the one saved; a new key id
	 * needs its secret. Audited without the secrets. Applies at once, to every payment from now on.
	 */
	@Transactional
	public PaymentGatewaySettingsResponse updateKeys(UUID actorId, PaymentGatewayKeysRequest request) {
		RazorpayKeys before = jdbc.queryForObject("SELECT * FROM payment_gateway_settings WHERE id = 1 FOR UPDATE",
				(rs, i) -> credentials.from(rs));
		String keyId = request.keyId().strip();
		String keySecret = blankToNull(request.keySecret());
		String webhookSecret = blankToNull(request.webhookSecret());
		boolean sameKey = before.keysFromAdmin() && before.keyId().equals(keyId);
		if (keySecret == null && !sameKey) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter the key secret for this key id");
		}
		String merchant = blankToNull(request.merchantName());
		jdbc.update("""
				UPDATE payment_gateway_settings SET key_id = ?,
				       key_secret_encrypted = COALESCE(?, key_secret_encrypted),
				       webhook_secret_encrypted = COALESCE(?, webhook_secret_encrypted),
				       merchant_name = ?, keys_updated_at = ?, keys_updated_by = ?
				WHERE id = 1""", keyId,
				keySecret == null ? null : encryptor.encrypt(keySecret, RazorpayCredentials.KEY_SECRET_CONTEXT),
				webhookSecret == null ? null : encryptor.encrypt(webhookSecret, RazorpayCredentials.WEBHOOK_SECRET_CONTEXT),
				merchant, Timestamp.from(clock.instant()), actorId);
		audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_GATEWAY_KEYS_CHANGED, ENTITY, null)
			.withOldValue(keyAudit(before, false, false))
			.withNewValue(keyAudit(credentials.current(), keySecret != null, webhookSecret != null)));
		return view();
	}


	/** What is audited of the keys: never a secret, only which key id and whether a secret changed. */
	private static Map<String, Object> keyAudit(RazorpayKeys keys, boolean keySecretChanged, boolean webhookChanged) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("keyId", keys.enabled() ? keys.keyId() : null);
		values.put("source", keys.keysFromAdmin() ? "ADMIN" : "SERVER");
		values.put("webhookSource", keys.webhookFromAdmin() ? "ADMIN" : "SERVER");
		values.put("merchantName", keys.merchantName());
		if (keySecretChanged) {
			values.put("keySecretChanged", true);
		}
		if (webhookChanged) {
			values.put("webhookSecretChanged", true);
		}
		return values;
	}

	/** The last four characters of a secret, for recognising it; null when not set. */
	private static String hint(String secret) {
		return secret == null || secret.isBlank() ? null : FieldEncryptor.lastChars(secret, 4);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private String name(UUID userId) {
		if (userId == null) {
			return null;
		}
		UserAccount a = accounts.getAccounts(List.of(userId)).get(userId);
		return a == null ? null : (a.firstName() + " " + a.lastName()).strip();
	}

	/** Saves the settings; audited with the values before and after. Applies to payments started from now on. */
	@Transactional
	public PaymentGatewaySettingsResponse update(UUID actorId, PaymentGatewaySettingsRequest request) {
		GatewaySettings before = jdbc.queryForObject("SELECT * FROM payment_gateway_settings WHERE id = 1 FOR UPDATE",
				(rs, i) -> effective(rs));
		Map<String, Object> old = values(before.onlinePayments(), before.maxAmount(),
				(int) before.checkoutWindow().toMinutes());
		Map<String, Object> now = values(request.onlinePayments(), request.maxAmount(), request.checkoutWindowMinutes());
		jdbc.update("""
				UPDATE payment_gateway_settings SET online_payments_enabled = ?, max_amount = ?,
				       checkout_window_minutes = ?, updated_at = ?, updated_by = ?
				WHERE id = 1""", request.onlinePayments(), request.maxAmount(), request.checkoutWindowMinutes(),
				Timestamp.from(clock.instant()), actorId);
		if (!old.equals(now)) {
			audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_GATEWAY_SETTINGS_CHANGED, ENTITY, null)
				.withOldValue(old).withNewValue(now));
		}
		return view();
	}

	private GatewaySettings effective(ResultSet rs) throws SQLException {
		Boolean online = (Boolean) rs.getObject("online_payments_enabled");
		BigDecimal max = rs.getBigDecimal("max_amount");
		Integer checkoutMinutes = (Integer) rs.getObject("checkout_window_minutes");
		return new GatewaySettings(credentials.from(rs).enabled(), online == null || online,
				max == null ? defaultMaxAmount() : max.stripTrailingZeros(),
				checkoutMinutes == null ? razorpay.checkoutWindow() : Duration.ofMinutes(checkoutMinutes));
	}

	private BigDecimal defaultMaxAmount() {
		return razorpay.maxAmount() == null ? BigDecimal.ZERO : razorpay.maxAmount();
	}


	private MoneyResponse rupees(BigDecimal amount) {
		return MoneyResponse.from(Money.of(amount, money.defaultCurrency()));
	}

	private static Map<String, Object> values(boolean online, BigDecimal max, int checkoutMinutes) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("onlinePayments", online);
		values.put("maxAmount", max.stripTrailingZeros().toPlainString());
		values.put("checkoutWindowMinutes", checkoutMinutes);
		return values;
	}

}
