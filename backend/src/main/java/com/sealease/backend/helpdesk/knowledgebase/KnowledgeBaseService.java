package com.sealease.backend.helpdesk.knowledgebase;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The knowledge base: articles in categories, each with a visibility (PUBLIC: anyone; CUSTOMER: signed-in
 * customers; INTERNAL: staff only) and a status (DRAFT, PUBLISHED, ARCHIVED). Readers only ever get published
 * articles their audience may see. Every saved edit is kept as an immutable version. Bodies are plain text with light
 * Markdown (headings, lists, paragraphs) and are rendered as text, never as HTML.
 */
@Service
public class KnowledgeBaseService {

	/** Who is reading: decides which visibilities are included. */
	public enum Audience {
		PUBLIC(List.of("PUBLIC")), CUSTOMER(List.of("PUBLIC", "CUSTOMER")), STAFF(List.of("PUBLIC", "CUSTOMER", "INTERNAL"));

		final List<String> visibilities;

		Audience(List<String> visibilities) {
			this.visibilities = visibilities;
		}
	}

	public record Category(UUID id, String code, String name, String description, int sortOrder, long articles) {
	}

	public record Summary(UUID id, String slug, String title, String summary, String categoryCode, String categoryName,
			String visibility, String status, List<String> tags, Instant publishedAt, Instant updatedAt, long views,
			int helpful, int notHelpful) {
	}

	public record Article(Summary summary, String body, int currentVersion, String authorName, String updatedByName,
			List<Summary> related) {
	}

	public record Version(int version, String title, String summary, String body, String editedByName, Instant createdAt) {
	}

	public record ArticleRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 300) String summary,
			@NotBlank @Size(max = 100000) String body, @NotNull String categoryCode,
			@NotNull @Pattern(regexp = "PUBLIC|CUSTOMER|INTERNAL") String visibility, @Size(max = 20) List<@Size(max = 40) String> tags,
			@Size(max = 10) List<UUID> relatedIds) {
	}

	public record FeedbackStats(UUID articleId, String title, int helpful, int notHelpful, List<Comment> comments) {
	}

	public record Comment(boolean helpful, String comment, Instant createdAt) {
	}

	public record Page<T>(List<T> content, long totalElements, int page, int size) {
	}

	private static final String SUMMARY_COLUMNS = """
			a.id, a.slug, a.title, a.summary, c.code, c.name, a.visibility, a.status, a.tags, a.published_at, a.updated_at,
			a.view_count, a.helpful_count, a.not_helpful_count""";

	private static final RowMapper<Summary> SUMMARY = (rs, i) -> new Summary(rs.getObject(1, UUID.class), rs.getString(2),
			rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
			tags(rs.getString(9)), rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(),
			rs.getTimestamp(11).toInstant(), rs.getLong(12), rs.getInt(13), rs.getInt(14));

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public KnowledgeBaseService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	// ------------------------------------------------------------------------------------------------ reading

	@Transactional(readOnly = true)
	public List<Category> categories(Audience audience) {
		return jdbc.query("""
				SELECT c.id, c.code, c.name, c.description, c.sort_order,
				       (SELECT count(*) FROM kb_articles a WHERE a.category_id = c.id AND a.status = 'PUBLISHED'
				          AND a.visibility = ANY (?))
				FROM kb_categories c WHERE c.active ORDER BY c.sort_order, c.name""",
				(rs, i) -> new Category(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getInt(5), rs.getLong(6)), (Object) audience.visibilities.toArray(String[]::new));
	}

	/**
	 * Published articles the audience may read, best match first when searching (full text over title, summary, tags
	 * and body, plus title substrings), otherwise by {@code sort}: popular or recent.
	 */
	@Transactional(readOnly = true)
	public Page<Summary> search(Audience audience, String q, String categoryCode, String sort, int page, int size) {
		return query(audience.visibilities, List.of("PUBLISHED"), q, categoryCode, sort, page, size);
	}

	/** For editors: every article, in any status and visibility. */
	@Transactional(readOnly = true)
	public Page<Summary> manage(String q, String categoryCode, String status, int page, int size) {
		return query(Audience.STAFF.visibilities, status == null || status.isBlank() ? List.of("DRAFT", "PUBLISHED", "ARCHIVED")
				: List.of(status), q, categoryCode, "recent", page, size);
	}

	private Page<Summary> query(List<String> visibilities, List<String> statuses, String q, String categoryCode, String sort,
			int page, int size) {
		int limit = Math.clamp(size, 1, 50);
		int offset = Math.max(0, page) * limit;
		String text = q == null ? "" : q.strip();
		if (text.length() > 200) {
			text = text.substring(0, 200);
		}
		boolean searching = !text.isEmpty();
		String like = "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		String where = """
				FROM kb_articles a JOIN kb_categories c ON c.id = a.category_id
				WHERE a.visibility = ANY (?) AND a.status = ANY (?) AND (CAST(? AS varchar) IS NULL OR c.code = CAST(? AS varchar))
				  AND (NOT ? OR a.search_vector @@ websearch_to_tsquery('english', ?) OR lower(a.title) LIKE ? ESCAPE '\\'
				       OR lower(a.tags) LIKE ? ESCAPE '\\')""";
		Object[] args = { visibilities.toArray(String[]::new), statuses.toArray(String[]::new), blank(categoryCode),
				blank(categoryCode), searching, text, like, like };
		Long total = jdbc.queryForObject("SELECT count(*) " + where, Long.class, args);
		String order = searching ? "ts_rank(a.search_vector, websearch_to_tsquery('english', ?)) DESC, a.view_count DESC"
				: "popular".equals(sort) ? "a.view_count DESC, a.helpful_count DESC, a.title"
				: "coalesce(a.published_at, a.updated_at) DESC";
		List<Object> all = new ArrayList<>(Arrays.asList(args));
		if (searching) {
			all.add(text);
		}
		all.add(limit);
		all.add(offset);
		List<Summary> rows = jdbc.query("SELECT " + SUMMARY_COLUMNS + " " + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
				SUMMARY, all.toArray());
		return new Page<>(rows, total == null ? 0 : total, Math.max(0, page), limit);
	}

	/** A published article the audience may read; counts the view. */
	@Transactional
	public Article read(Audience audience, String slug) {
		Summary s = find("a.slug = ? AND a.status = 'PUBLISHED' AND a.visibility = ANY (?)", slug,
				audience.visibilities.toArray(String[]::new))
			.orElseThrow(() -> new ResourceNotFoundException("Article", slug));
		jdbc.update("UPDATE kb_articles SET view_count = view_count + 1 WHERE id = ?", s.id());
		return full(s, audience);
	}

	/** Any article, for editors. */
	@Transactional(readOnly = true)
	public Article edit(UUID id) {
		return full(find("a.id = ?", id).orElseThrow(() -> new ResourceNotFoundException("Article", id)), Audience.STAFF);
	}

	private Article full(Summary s, Audience audience) {
		Object[] row = jdbc.queryForObject("""
				SELECT a.body, a.current_version, au.first_name || ' ' || au.last_name, ub.first_name || ' ' || ub.last_name
				FROM kb_articles a JOIN users au ON au.id = a.author_id JOIN users ub ON ub.id = a.updated_by WHERE a.id = ?""",
				(rs, i) -> new Object[] { rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4) }, s.id());
		return new Article(s, (String) row[0], (int) row[1], (String) row[2], (String) row[3], related(s, audience));
	}

	/** Linked articles first, then others in the same category, published and visible to the audience. */
	private List<Summary> related(Summary s, Audience audience) {
		Object vis = audience.visibilities.toArray(String[]::new);
		List<Summary> linked = jdbc.query("SELECT " + SUMMARY_COLUMNS + """
				 FROM kb_article_related r JOIN kb_articles a ON a.id = r.related_id JOIN kb_categories c ON c.id = a.category_id
				WHERE r.article_id = ? AND a.status = 'PUBLISHED' AND a.visibility = ANY (?) ORDER BY a.title""", SUMMARY, s.id(), vis);
		Set<UUID> seen = linked.stream().map(Summary::id).collect(Collectors.toSet());
		seen.add(s.id());
		List<Summary> out = new ArrayList<>(linked);
		if (out.size() < 5) {
			jdbc.query("SELECT " + SUMMARY_COLUMNS + """
					 FROM kb_articles a JOIN kb_categories c ON c.id = a.category_id
					WHERE c.code = ? AND a.status = 'PUBLISHED' AND a.visibility = ANY (?) ORDER BY a.view_count DESC LIMIT 10""",
					SUMMARY, s.categoryCode(), vis).stream()
				.filter(x -> !seen.contains(x.id())).limit(5 - out.size()).forEach(out::add);
		}
		return out;
	}

	private Optional<Summary> find(String condition, Object... args) {
		return jdbc.query("SELECT " + SUMMARY_COLUMNS + " FROM kb_articles a JOIN kb_categories c ON c.id = a.category_id WHERE "
				+ condition, SUMMARY, args).stream().findFirst();
	}

	// ------------------------------------------------------------------------------------------------ editing

	/** A new draft (version 1). */
	@Transactional
	public Article create(UUID actorId, ArticleRequest r) {
		UUID category = categoryId(r.categoryCode());
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		String slug = uniqueSlug(r.title(), null);
		jdbc.update("""
				INSERT INTO kb_articles (id, slug, category_id, title, summary, body, tags, visibility, status, current_version,
				  author_id, updated_by, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 1, ?, ?, ?, ?)""", id, slug, category, r.title().strip(),
				r.summary().strip(), r.body().strip(), tagText(r.tags()), r.visibility(), actorId, actorId, Timestamp.from(now),
				Timestamp.from(now));
		version(id, 1, r, actorId, now);
		setRelated(id, r.relatedIds());
		audit.record(AuditRecord.of(actorId, AuditAction.KB_ARTICLE_SAVED, "KB_ARTICLE", id)
			.withNewValue(Map.of("title", r.title().strip(), "version", 1, "visibility", r.visibility())));
		return edit(id);
	}

	/** Saves an edit as the next version; a published article stays published with the new content. */
	@Transactional
	public Article update(UUID actorId, UUID id, long expectedVersion, ArticleRequest r) {
		Integer current = jdbc.query("SELECT current_version FROM kb_articles WHERE id = ? FOR UPDATE",
				(rs, i) -> rs.getInt(1), id).stream().findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Article", id));
		if (expectedVersion > 0 && current != expectedVersion) {
			throw new BusinessException(ErrorCode.CONFLICT,
					"Someone else saved this article (now version " + current + "); reload it before saving");
		}
		int next = current + 1;
		Instant now = clock.instant();
		jdbc.update("""
				UPDATE kb_articles SET category_id = ?, title = ?, summary = ?, body = ?, tags = ?, visibility = ?,
				  current_version = ?, updated_by = ?, updated_at = ?, version = version + 1 WHERE id = ?""",
				categoryId(r.categoryCode()), r.title().strip(), r.summary().strip(), r.body().strip(), tagText(r.tags()),
				r.visibility(), next, actorId, Timestamp.from(now), id);
		version(id, next, r, actorId, now);
		setRelated(id, r.relatedIds());
		audit.record(AuditRecord.of(actorId, AuditAction.KB_ARTICLE_SAVED, "KB_ARTICLE", id)
			.withNewValue(Map.of("title", r.title().strip(), "version", next, "visibility", r.visibility())));
		return edit(id);
	}

	@Transactional
	public Article publish(UUID actorId, UUID id) {
		return setStatus(actorId, id, "PUBLISHED", AuditAction.KB_ARTICLE_PUBLISHED);
	}

	@Transactional
	public Article archive(UUID actorId, UUID id) {
		return setStatus(actorId, id, "ARCHIVED", AuditAction.KB_ARTICLE_ARCHIVED);
	}

	/** Back to draft (unpublished): readers no longer see it. */
	@Transactional
	public Article unpublish(UUID actorId, UUID id) {
		return setStatus(actorId, id, "DRAFT", AuditAction.KB_ARTICLE_SAVED);
	}

	private Article setStatus(UUID actorId, UUID id, String status, AuditAction action) {
		String previous = jdbc.query("SELECT status FROM kb_articles WHERE id = ? FOR UPDATE", (rs, i) -> rs.getString(1), id)
			.stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Article", id));
		if (previous.equals(status)) {
			throw new BusinessException(ErrorCode.CONFLICT, "The article is already " + status.toLowerCase(Locale.ROOT));
		}
		Instant now = clock.instant();
		jdbc.update("""
				UPDATE kb_articles SET status = ?, published_at = CASE WHEN ? = 'PUBLISHED' THEN ? ELSE published_at END,
				  updated_by = ?, updated_at = ?, version = version + 1 WHERE id = ?""",
				status, status, Timestamp.from(now), actorId, Timestamp.from(now), id);
		audit.record(AuditRecord.of(actorId, action, "KB_ARTICLE", id)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", status)));
		return edit(id);
	}

	@Transactional(readOnly = true)
	public List<Version> versions(UUID id) {
		return jdbc.query("""
				SELECT v.version, v.title, v.summary, v.body, u.first_name || ' ' || u.last_name, v.created_at
				FROM kb_article_versions v JOIN users u ON u.id = v.edited_by WHERE v.article_id = ? ORDER BY v.version DESC""",
				(rs, i) -> new Version(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
						rs.getTimestamp(6).toInstant()), id);
	}

	private void version(UUID id, int version, ArticleRequest r, UUID actorId, Instant now) {
		jdbc.update("""
				INSERT INTO kb_article_versions (id, article_id, version, title, summary, body, edited_by, created_at)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?)""", id, version, r.title().strip(), r.summary().strip(),
				r.body().strip(), actorId, Timestamp.from(now));
	}

	private void setRelated(UUID id, List<UUID> related) {
		jdbc.update("DELETE FROM kb_article_related WHERE article_id = ?", id);
		for (UUID other : new LinkedHashSet<>(related == null ? List.<UUID>of() : related)) {
			if (other.equals(id)) {
				continue;
			}
			Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM kb_articles WHERE id = ?)", Boolean.class, other);
			if (!Boolean.TRUE.equals(exists)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A related article was not found");
			}
			jdbc.update("INSERT INTO kb_article_related (article_id, related_id) VALUES (?, ?)", id, other);
		}
	}

	private UUID categoryId(String code) {
		return jdbc.query("SELECT id FROM kb_categories WHERE code = ? AND active", (rs, i) -> rs.getObject(1, UUID.class), code)
			.stream().findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown knowledge base category " + code));
	}

	private String uniqueSlug(String title, UUID except) {
		String base = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (base.isEmpty()) {
			base = "article";
		}
		base = base.length() > 100 ? base.substring(0, 100).replaceAll("-+$", "") : base;
		String slug = base;
		for (int n = 2; Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM kb_articles WHERE slug = ? AND (CAST(? AS uuid) IS NULL OR id <> CAST(? AS uuid)))",
				Boolean.class, slug, except, except)); n++) {
			slug = base + "-" + n;
		}
		return slug;
	}

	// ----------------------------------------------------------------------------------------------- feedback

	/** A signed-in reader says whether an article helped, once per article. */
	@Transactional
	public void feedback(Audience audience, UUID userId, String slug, boolean helpful, String comment) {
		Summary s = find("a.slug = ? AND a.status = 'PUBLISHED' AND a.visibility = ANY (?)", slug,
				audience.visibilities.toArray(String[]::new))
			.orElseThrow(() -> new ResourceNotFoundException("Article", slug));
		try {
			jdbc.update("INSERT INTO kb_article_feedback (id, article_id, user_id, helpful, comment, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)",
					s.id(), userId, helpful, blank(comment), Timestamp.from(clock.instant()));
		}
		catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.CONFLICT, "You have already rated this article");
		}
		jdbc.update(helpful ? "UPDATE kb_articles SET helpful_count = helpful_count + 1 WHERE id = ?"
				: "UPDATE kb_articles SET not_helpful_count = not_helpful_count + 1 WHERE id = ?", s.id());
	}

	@Transactional(readOnly = true)
	public FeedbackStats feedbackFor(UUID id) {
		Summary s = find("a.id = ?", id).orElseThrow(() -> new ResourceNotFoundException("Article", id));
		List<Comment> comments = jdbc.query("""
				SELECT helpful, comment, created_at FROM kb_article_feedback WHERE article_id = ? AND comment IS NOT NULL
				ORDER BY created_at DESC LIMIT 100""",
				(rs, i) -> new Comment(rs.getBoolean(1), rs.getString(2), rs.getTimestamp(3).toInstant()), id);
		return new FeedbackStats(id, s.title(), s.helpful(), s.notHelpful(), comments);
	}

	private static String tagText(List<String> tags) {
		if (tags == null) {
			return "";
		}
		String text = tags.stream().map(t -> t.strip().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty()).distinct()
			.collect(Collectors.joining(","));
		return text.length() > 400 ? text.substring(0, 400) : text;
	}

	private static List<String> tags(String text) {
		return text == null || text.isBlank() ? List.of() : List.of(text.split(","));
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

}
