package com.sealease.backend.terms.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.terms.dto.TermsRequest;
import com.sealease.backend.terms.dto.TermsView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The website's terms and conditions, which super administrators maintain (TERMS_MANAGE). Each save is a new version,
 * in force from then on; every account records the version it accepted when it signed up.
 */
@Service
public class TermsService {

	private static final String ENTITY = "TERMS";

	private static final String SELECT = """
			SELECT t.id, t.version, t.title, t.body, t.created_at,
			       nullif(trim(concat_ws(' ', u.first_name, u.last_name)), '') AS author
			FROM terms_versions t LEFT JOIN users u ON u.id = t.created_by
			""";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public TermsService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** The version in force. */
	@Transactional(readOnly = true)
	public TermsView.Version current() {
		return jdbc.query(SELECT + " ORDER BY t.version DESC LIMIT 1", (rs, i) -> version(rs, true)).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"No terms and conditions have been written yet"));
	}

	@Transactional(readOnly = true)
	public TermsView view() {
		List<TermsView.Version> history = jdbc.query(SELECT + " ORDER BY t.version DESC LIMIT 20",
				(rs, i) -> version(rs, i == 0));
		TermsView.Version current = history.isEmpty() ? null : history.getFirst();
		Long accepted = current == null ? 0L
				: jdbc.queryForObject("SELECT count(*) FROM users WHERE terms_version = ?", Long.class, current.version());
		return new TermsView(current, accepted == null ? 0 : accepted, history);
	}

	/** Saves new wording as the next version, in force from now on. */
	@Transactional
	public TermsView.Version save(UUID actorId, TermsRequest request) {
		String title = request.title().strip();
		String body = normalise(request.body());
		jdbc.execute("LOCK TABLE terms_versions IN EXCLUSIVE MODE");
		Integer last = jdbc.queryForObject("SELECT max(version) FROM terms_versions", Integer.class);
		int next = (last == null ? 0 : last) + 1;
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO terms_versions (id, version, title, body, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				id, next, title, body, actorId, Timestamp.from(clock.instant()));
		audit.record(AuditRecord.of(actorId, AuditAction.TERMS_UPDATED, ENTITY, id)
			.withNewValue(Map.of("version", next, "title", title)));
		return current();
	}

	/** Records that the account accepted the version in force now. */
	@Transactional
	public void accept(UUID userId) {
		jdbc.update("UPDATE users SET terms_version = ?, terms_accepted_at = ? WHERE id = ?", current().version(),
				Timestamp.from(clock.instant()), userId);
	}

	/** Windows line breaks become plain ones, and trailing spaces go, so stored wording is consistent. */
	private static String normalise(String body) {
		return body.replace("\r\n", "\n").lines().map(String::stripTrailing).collect(Collectors.joining("\n")).strip();
	}

	private static TermsView.Version version(ResultSet rs, boolean withBody) throws SQLException {
		return new TermsView.Version(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("title"),
				withBody ? rs.getString("body") : null, rs.getTimestamp("created_at").toInstant(), rs.getString("author"));
	}

}
