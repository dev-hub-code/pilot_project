package com.sealease.backend.announcement.service;

import com.sealease.backend.announcement.dto.AnnouncementAudience;
import com.sealease.backend.announcement.dto.AnnouncementRequest;
import com.sealease.backend.announcement.dto.AnnouncementResponse;
import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.permission.PermissionCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Announcements (ANNOUNCEMENT_MANAGE): one message delivered as a notification to every active account in the
 * audience (suspended and disabled accounts never get one), in a single statement and the same transaction as the announcement itself, so either everyone gets it or
 * no one does. Announcements are not emailed: the per-notification email (notification.created) is for messages about
 * a person's own account, and mailing every account at once is a different job (bulk sending, unsubscribe links).
 */
@Service
public class AnnouncementService {

	/** The notification type: the web app shows these in full. */
	public static final String NOTIFICATION_TYPE = "ANNOUNCEMENT";

	private static final String ENTITY = "ANNOUNCEMENT";

	/** Active accounts holding the investor portal through any of their roles. */
	private static final String INVESTOR = """
			EXISTS (SELECT 1 FROM user_roles ur
			        JOIN role_permissions rp ON rp.role_id = ur.role_id
			        JOIN permissions p ON p.id = rp.permission_id
			        WHERE ur.user_id = u.id AND p.code = '%s')
			""".formatted(PermissionCode.INVESTOR_PORTAL.name());

	/** On the network referral scheme (made by an administrator); every other investor is online. */
	private static final String NETWORK = "EXISTS (SELECT 1 FROM network_investors ni WHERE ni.user_id = u.id)";

	private static final String SELECT = """
			SELECT a.id, a.title, a.body, a.link, a.audience, a.recipients, a.created_at,
			       nullif(trim(concat_ws(' ', u.first_name, u.last_name)), '') AS author
			FROM announcements a LEFT JOIN users u ON u.id = a.created_by
			""";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public AnnouncementService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** Sends the announcement to every active account in its audience; returns it with the number reached. */
	@Transactional
	public AnnouncementResponse publish(UUID actorId, AnnouncementRequest request) {
		UUID id = UUID.randomUUID();
		String title = request.title().strip();
		String body = normalise(request.body());
		String link = request.link() == null || request.link().isBlank() ? null : request.link().strip();
		Timestamp now = Timestamp.from(clock.instant());
		int recipients = jdbc.update("""
				INSERT INTO notifications (id, user_id, type, title, body, link, created_at)
				SELECT gen_random_uuid(), u.id, ?, ?, ?, ?, ?
				FROM users u
				WHERE u.status = 'ACTIVE' AND %s
				""".formatted(audienceFilter(request.audience())), NOTIFICATION_TYPE, title, body, link, now);
		jdbc.update("""
				INSERT INTO announcements (id, title, body, link, audience, recipients, created_by, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", id, title, body, link, request.audience().name(), recipients, actorId, now);
		audit.record(AuditRecord.of(actorId, AuditAction.ANNOUNCEMENT_PUBLISHED, ENTITY, id)
			.withNewValue(Map.of("title", title, "audience", request.audience().name(), "recipients", recipients)));
		return get(id);
	}

	/** Sent announcements, newest first. */
	@Transactional(readOnly = true)
	public Page<AnnouncementResponse> list(Pageable pageable) {
		List<AnnouncementResponse> page = jdbc.query(SELECT + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
				AnnouncementService::row, pageable.getPageSize(), pageable.getOffset());
		Long total = jdbc.queryForObject("SELECT count(*) FROM announcements", Long.class);
		return new PageImpl<>(page, pageable, total == null ? 0 : total);
	}

	private AnnouncementResponse get(UUID id) {
		return jdbc.queryForObject(SELECT + " WHERE a.id = ?", AnnouncementService::row, id);
	}

	private static String audienceFilter(AnnouncementAudience audience) {
		return switch (audience) {
			case EVERYONE -> "TRUE";
			case INVESTORS -> INVESTOR;
			case ONLINE_INVESTORS -> INVESTOR + " AND NOT " + NETWORK;
			case NETWORK_INVESTORS -> INVESTOR + " AND " + NETWORK;
			case STAFF -> "NOT " + INVESTOR;
		};
	}

	/** Windows line breaks become plain ones and the ends are trimmed, so the stored text is consistent. */
	private static String normalise(String body) {
		return body.replace("\r\n", "\n").strip();
	}

	private static AnnouncementResponse row(ResultSet rs, int i) throws SQLException {
		Instant createdAt = rs.getTimestamp("created_at").toInstant();
		return new AnnouncementResponse(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body"),
				rs.getString("link"), AnnouncementAudience.valueOf(rs.getString("audience")), rs.getInt("recipients"),
				createdAt, rs.getString("author"));
	}

}
