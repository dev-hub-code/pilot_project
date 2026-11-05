package com.sealease.backend.payment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.payment.dto.PaymentGatewaySettingsRequest;
import com.sealease.backend.payment.dto.PaymentGatewaySettingsResponse;
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
 * investors pay through Razorpay at all, whether bank transfer into a virtual account is offered, the most one checkout
 * payment can be, and how long an order is held while the investor pays. Until an admin saves, the server's
 * configuration ({@code app.payments.razorpay.*}) applies. The API keys and webhook secret stay in the environment and
 * are never stored or shown here.
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
	private final Clock clock;

	public PaymentGatewaySettingsService(JdbcTemplate jdbc, RazorpayProperties razorpay, MoneyProperties money,
			UserAccountService accounts, AuditService audit, Clock clock) {
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
			Timestamp updatedAt = rs.getTimestamp("updated_at");
			UUID updatedBy = rs.getObject("updated_by", UUID.class);
			String by = null;
			if (updatedBy != null) {
				UserAccount a = accounts.getAccounts(List.of(updatedBy)).get(updatedBy);
				by = a == null ? null : (a.firstName() + " " + a.lastName()).strip();
			}
			return new PaymentGatewaySettingsResponse(razorpay.enabled(), mode(), !razorpay.webhookSecret().isBlank(),
					now.onlinePayments(), now.virtualAccounts(), rupees(now.maxAmount()),
					(int) now.checkoutWindow().toMinutes(), (int) now.virtualAccountWindow().toHours(),
					new PaymentGatewaySettingsResponse.Defaults(true, razorpay.virtualAccounts(),
							rupees(defaultMaxAmount()), (int) razorpay.checkoutWindow().toMinutes(),
							(int) Math.max(1, razorpay.virtualAccountWindow().toHours())),
					updatedAt == null ? null : updatedAt.toInstant(), by);
		});
	}

	/** Saves the settings; audited with the values before and after. Applies to payments started from now on. */
	@Transactional
	public PaymentGatewaySettingsResponse update(UUID actorId, PaymentGatewaySettingsRequest request) {
		GatewaySettings before = jdbc.queryForObject("SELECT * FROM payment_gateway_settings WHERE id = 1 FOR UPDATE",
				(rs, i) -> effective(rs));
		Map<String, Object> old = values(before.onlinePayments(), before.virtualAccounts(), before.maxAmount(),
				(int) before.checkoutWindow().toMinutes(), (int) before.virtualAccountWindow().toHours());
		Map<String, Object> now = values(request.onlinePayments(), request.virtualAccounts(), request.maxAmount(),
				request.checkoutWindowMinutes(), request.virtualAccountWindowHours());
		jdbc.update("""
				UPDATE payment_gateway_settings SET online_payments_enabled = ?, virtual_accounts_enabled = ?,
				       max_amount = ?, checkout_window_minutes = ?, virtual_account_window_hours = ?, updated_at = ?,
				       updated_by = ?
				WHERE id = 1""", request.onlinePayments(), request.virtualAccounts(), request.maxAmount(),
				request.checkoutWindowMinutes(), request.virtualAccountWindowHours(), Timestamp.from(clock.instant()),
				actorId);
		if (!old.equals(now)) {
			audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_GATEWAY_SETTINGS_CHANGED, ENTITY, null)
				.withOldValue(old).withNewValue(now));
		}
		return view();
	}

	private GatewaySettings effective(ResultSet rs) throws SQLException {
		Boolean online = (Boolean) rs.getObject("online_payments_enabled");
		Boolean virtualAccounts = (Boolean) rs.getObject("virtual_accounts_enabled");
		BigDecimal max = rs.getBigDecimal("max_amount");
		Integer checkoutMinutes = (Integer) rs.getObject("checkout_window_minutes");
		Integer vaHours = (Integer) rs.getObject("virtual_account_window_hours");
		return new GatewaySettings(razorpay.enabled(), online == null || online,
				virtualAccounts == null ? razorpay.virtualAccounts() : virtualAccounts,
				max == null ? defaultMaxAmount() : max.stripTrailingZeros(),
				checkoutMinutes == null ? razorpay.checkoutWindow() : Duration.ofMinutes(checkoutMinutes),
				vaHours == null ? razorpay.virtualAccountWindow() : Duration.ofHours(vaHours));
	}

	private BigDecimal defaultMaxAmount() {
		return razorpay.maxAmount() == null ? BigDecimal.ZERO : razorpay.maxAmount();
	}

	/** Test or live keys, from the key id's prefix; the key id itself is shown to investors' browsers anyway. */
	private String mode() {
		if (!razorpay.enabled()) {
			return null;
		}
		return razorpay.keyId().startsWith("rzp_live_") ? "live" : "test";
	}

	private MoneyResponse rupees(BigDecimal amount) {
		return MoneyResponse.from(Money.of(amount, money.defaultCurrency()));
	}

	private static Map<String, Object> values(boolean online, boolean virtualAccounts, BigDecimal max, int checkoutMinutes,
			int vaHours) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("onlinePayments", online);
		values.put("virtualAccounts", virtualAccounts);
		values.put("maxAmount", max.stripTrailingZeros().toPlainString());
		values.put("checkoutWindowMinutes", checkoutMinutes);
		values.put("virtualAccountWindowHours", vaHours);
		return values;
	}

}
