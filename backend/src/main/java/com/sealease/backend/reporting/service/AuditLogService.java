package com.sealease.backend.reporting.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditEntry;
import com.sealease.backend.audit.AuditLogFilter;
import com.sealease.backend.audit.AuditLogQuery;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.csv.Csv;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.reporting.dto.AuditLogEntryResponse;
import com.sealease.backend.reporting.dto.AuditLogEntryResponse.UserRef;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The audit log for staff with AUDIT_VIEW: search, one entry, and CSV export. Entries are returned with
 * the people they name (actor, and the account a user entry is about) resolved to email and name.
 */
@Service
public class AuditLogService {

	/** The most entries one export may hold; narrower filters are needed beyond it. */
	public static final int EXPORT_LIMIT = 10_000;
	private static final String USER_ENTITY = "USER";

	private final AuditLogQuery query;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final JsonMapper jsonMapper;

	public AuditLogService(AuditLogQuery query, UserAccountService accounts, AuditService audit, JsonMapper jsonMapper) {
		this.query = query;
		this.accounts = accounts;
		this.audit = audit;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param actorEmail only entries by the account with this email; no such account matches nothing
	 */
	@Transactional(readOnly = true)
	public PageResponse<AuditLogEntryResponse> search(AuditLogFilter filter, String actorEmail, Pageable pageable) {
		Optional<AuditLogFilter> resolved = withActor(filter, actorEmail);
		if (resolved.isEmpty()) {
			return PageResponse.from(Page.empty(pageable));
		}
		Page<AuditEntry> page = query.search(resolved.get(), pageable);
		return PageResponse.from(page.map(toResponses(page.getContent())::get));
	}

	@Transactional(readOnly = true)
	public AuditLogEntryResponse get(UUID id) {
		AuditEntry entry = query.find(id).orElseThrow(() -> new ResourceNotFoundException("Audit entry", id));
		return toResponses(List.of(entry)).get(entry);
	}

	/**
	 * The matching entries as CSV, newest first, and an AUDIT_LOG_EXPORTED entry recording who took them
	 * and with which filters.
	 */
	@Transactional
	public byte[] export(UUID actorId, AuditLogFilter filter, String actorEmail) {
		Optional<AuditLogFilter> resolved = withActor(filter, actorEmail);
		List<AuditEntry> entries = List.of();
		if (resolved.isPresent()) {
			long matching = query.search(resolved.get(), PageRequest.of(0, 1)).getTotalElements();
			if (matching > EXPORT_LIMIT) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "%,d entries match; an export holds at most %,d. Narrow the dates or filters."
					.formatted(matching, EXPORT_LIMIT));
			}
			entries = query.list(resolved.get(), EXPORT_LIMIT);
		}
		Map<AuditEntry, AuditLogEntryResponse> responses = toResponses(entries);
		StringBuilder out = new StringBuilder("﻿");
		out.append(Csv.line("time_utc", "action", "category", "actor_email", "actor_id", "entity_type", "entity_id",
				"subject_email", "ip_address", "user_agent", "correlation_id", "old_value", "new_value"));
		for (AuditEntry entry : entries) {
			AuditLogEntryResponse r = responses.get(entry);
			out.append(Csv.line(r.createdAt().toString(), r.action().name(), r.category().name(),
					r.actor() == null ? null : r.actor().email(), r.actor() == null ? null : r.actor().id().toString(),
					r.entityType(), r.entityId(), r.subject() == null ? null : r.subject().email(), r.ipAddress(),
					r.userAgent(), r.correlationId(), json(r.oldValue()), json(r.newValue())));
		}

		Map<String, Object> details = new LinkedHashMap<>(describe(filter));
		if (actorEmail != null) {
			details.put("actorEmail", actorEmail);
		}
		details.put("entries", entries.size());
		audit.record(AuditRecord.of(actorId, AuditAction.AUDIT_LOG_EXPORTED, "AUDIT_LOG", null).withNewValue(details));
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Each entry with its people resolved, in one lookup. */
	Map<AuditEntry, AuditLogEntryResponse> toResponses(Collection<AuditEntry> entries) {
		Set<UUID> ids = new HashSet<>();
		for (AuditEntry entry : entries) {
			if (entry.actorUserId() != null) {
				ids.add(entry.actorUserId());
			}
			subjectId(entry).ifPresent(ids::add);
		}
		Map<UUID, UserAccount> people = ids.isEmpty() ? Map.of() : accounts.getAccounts(ids);
		Map<AuditEntry, AuditLogEntryResponse> out = new LinkedHashMap<>();
		for (AuditEntry e : entries) {
			out.put(e, new AuditLogEntryResponse(e.id(), e.createdAt(), e.action(), e.category(),
					ref(e.actorUserId(), people), e.entityType(), e.entityId(),
					subjectId(e).map(id -> ref(id, people)).orElse(null), e.ipAddress(), e.userAgent(),
					e.correlationId(), e.oldValue(), e.newValue()));
		}
		return out;
	}

	/** A reference to the person, by email and name when the account still exists. */
	static UserRef ref(UUID id, Map<UUID, UserAccount> people) {
		if (id == null) {
			return null;
		}
		UserAccount account = people.get(id);
		return account == null ? new UserRef(id, null, null)
				: new UserRef(id, account.email(), (account.firstName() + " " + account.lastName()).strip());
	}

	/** The filter with the actor's email turned into their id; empty if no account has that email. */
	private Optional<AuditLogFilter> withActor(AuditLogFilter filter, String actorEmail) {
		if (actorEmail == null) {
			return Optional.of(filter);
		}
		return accounts.findByEmail(actorEmail).map(account -> new AuditLogFilter(filter.category(), filter.action(),
				account.id(), filter.entityType(), filter.entityId(), filter.ipAddress(), filter.from(), filter.to()));
	}

	private static Optional<UUID> subjectId(AuditEntry entry) {
		if (!USER_ENTITY.equals(entry.entityType()) || entry.entityId() == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(entry.entityId()));
		} catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private static Map<String, Object> describe(AuditLogFilter filter) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (filter.category() != null) {
			out.put("category", filter.category());
		}
		if (filter.action() != null) {
			out.put("action", filter.action());
		}
		if (filter.actorUserId() != null) {
			out.put("actorUserId", filter.actorUserId());
		}
		if (filter.entityType() != null) {
			out.put("entityType", filter.entityType());
		}
		if (filter.entityId() != null) {
			out.put("entityId", filter.entityId());
		}
		if (filter.ipAddress() != null) {
			out.put("ipAddress", filter.ipAddress());
		}
		if (filter.from() != null) {
			out.put("from", filter.from().toString());
		}
		if (filter.to() != null) {
			out.put("to", filter.to().toString());
		}
		return out;
	}

	private String json(Map<String, Object> value) {
		return value == null ? null : jsonMapper.writeValueAsString(value);
	}

}
