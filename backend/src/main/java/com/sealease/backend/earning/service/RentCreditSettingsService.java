package com.sealease.backend.earning.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.earning.dto.PublicRentCredit;
import com.sealease.backend.earning.dto.RentCreditSettingsResponse;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How often rent is credited, set by an admin (one row, {@code rent_credit_settings}). Until it is set, the configured
 * {@code app.payouts.credit-interval} applies. Crediting is cumulative, so a change applies from the next run: the
 * rent accrued since the last credit is credited at the next boundary of the new interval.
 */
@Service
public class RentCreditSettingsService {

	/** What an admin may choose: whole minutes dividing a day, from 5 minutes to a day. */
	static final List<Long> OPTIONS = List.of(5L, 10L, 15L, 30L, 60L, 120L, 180L, 240L, 360L, 480L, 720L, 1440L);
	private static final String ENTITY = "RENT_CREDIT_SETTINGS";

	private final JdbcTemplate jdbc;
	private final RentCreditProperties properties;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final Clock clock;

	public RentCreditSettingsService(JdbcTemplate jdbc, RentCreditProperties properties, UserAccountService accounts,
			AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/** The interval in force. */
	public CreditInterval current() {
		Integer minutes = jdbc.queryForObject("SELECT credit_interval_minutes FROM rent_credit_settings WHERE id = 1",
				Integer.class);
		return minutes == null ? properties.interval() : CreditInterval.ofMinutes(minutes);
	}

	public PublicRentCredit forPublic() {
		CreditInterval interval = current();
		return new PublicRentCredit(interval.minutes(), interval.label());
	}

	@Transactional(readOnly = true)
	public RentCreditSettingsResponse get() {
		return jdbc.queryForObject("SELECT credit_interval_minutes, updated_at, updated_by FROM rent_credit_settings WHERE id = 1",
				(rs, i) -> {
					int set = rs.getInt("credit_interval_minutes");
					boolean isSet = !rs.wasNull();
					Timestamp updatedAt = rs.getTimestamp("updated_at");
					UUID updatedBy = rs.getObject("updated_by", UUID.class);
					CreditInterval interval = isSet ? CreditInterval.ofMinutes(set) : properties.interval();
					String by = null;
					if (isSet && updatedBy != null) {
						UserAccount a = accounts.getAccounts(List.of(updatedBy)).get(updatedBy);
						by = a == null ? null : (a.firstName() + " " + a.lastName()).strip();
					}
					return new RentCreditSettingsResponse(interval.minutes(), interval.label(),
							properties.interval().minutes(),
							OPTIONS.stream().map(m -> new RentCreditSettingsResponse.Option(m, CreditInterval.ofMinutes(m).label()))
								.toList(),
							isSet && updatedAt != null ? updatedAt.toInstant() : null, by);
				});
	}

	/** Applies from the next crediting run. */
	@Transactional
	public RentCreditSettingsResponse update(UUID actorId, int minutes) {
		if (!OPTIONS.contains((long) minutes)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose one of the offered intervals");
		}
		Integer before = jdbc.queryForObject(
				"SELECT credit_interval_minutes FROM rent_credit_settings WHERE id = 1 FOR UPDATE", Integer.class);
		long effectiveBefore = before == null ? properties.interval().minutes() : before;
		if (before == null || effectiveBefore != minutes) {
			jdbc.update("UPDATE rent_credit_settings SET credit_interval_minutes = ?, updated_at = ?, updated_by = ? WHERE id = 1",
					minutes, Timestamp.from(clock.instant()), actorId);
			audit.record(AuditRecord.of(actorId, AuditAction.RENT_CREDIT_INTERVAL_CHANGED, ENTITY, null)
				.withOldValue(Map.of("intervalMinutes", effectiveBefore))
				.withNewValue(Map.of("intervalMinutes", minutes)));
		}
		return get();
	}

}
