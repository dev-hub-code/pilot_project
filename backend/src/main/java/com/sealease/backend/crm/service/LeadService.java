package com.sealease.backend.crm.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.crm.dto.ActivityRequest;
import com.sealease.backend.crm.dto.Assignee;
import com.sealease.backend.crm.dto.LeadDetail;
import com.sealease.backend.crm.dto.LeadRequest;
import com.sealease.backend.crm.dto.LeadResponse;
import com.sealease.backend.crm.dto.LeadSearchCriteria;
import com.sealease.backend.crm.dto.PipelineStage;
import com.sealease.backend.crm.entity.ActivityType;
import com.sealease.backend.crm.entity.Lead;
import com.sealease.backend.crm.entity.LeadActivity;
import com.sealease.backend.crm.entity.LeadSource;
import com.sealease.backend.crm.entity.LeadStage;
import com.sealease.backend.crm.repository.LeadActivityRepository;
import com.sealease.backend.crm.repository.LeadRepository;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.EmailNormalizer;
import com.sealease.backend.user.service.UserAccountService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The sales pipeline as staff work it.
 *
 * <p>Visibility: holders of LEAD_ASSIGN (managers) see and change every lead. Everyone else with
 * LEAD_VIEW sees leads assigned to them and unassigned ones, and may change only their own (they
 * claim an unassigned lead first). Leads outside a viewer's scope answer "not found".
 */
@Service
public class LeadService {

	private static final String ENTITY = "LEAD";

	private final LeadRepository leads;
	private final LeadActivityRepository activities;
	private final UserAccountService accounts;
	private final UserRoleService roles;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final Clock clock;

	public LeadService(LeadRepository leads, LeadActivityRepository activities, UserAccountService accounts,
			UserRoleService roles, OutboxPublisher outbox, AuditService audit, Clock clock) {
		this.leads = leads;
		this.activities = activities;
		this.accounts = accounts;
		this.roles = roles;
		this.outbox = outbox;
		this.audit = audit;
		this.clock = clock;
	}

	/** Who is asking: their id and whether they manage (LEAD_ASSIGN) the whole pipeline. */
	public record Viewer(UUID userId, boolean manager) {

		boolean sees(Lead lead) {
			return manager || lead.getOwnerId() == null || userId.equals(lead.getOwnerId());
		}

		boolean mayChange(Lead lead) {
			return manager || userId.equals(lead.getOwnerId());
		}

	}

	// ----------------------------------------------------------------------------- writes

	@Transactional
	public LeadDetail create(Viewer viewer, LeadRequest request) {
		Lead.Contact contact = contact(request);
		if (contact.email() != null) {
			leads.lockOpenByEmail(contact.email()).ifPresent(existing -> {
				throw new BusinessException(ErrorCode.CONFLICT,
						"An open lead for this email already exists (" + existing.getReference() + ")");
			});
		}
		UUID owner = viewer.manager() ? request.ownerId() : viewer.userId();
		if (owner != null && viewer.manager()) {
			requireCanWorkLeads(owner);
		}
		Lead lead = new Lead("LD-" + String.format("%06d", leads.nextNumber()), LeadSource.STAFF, contact, null,
				viewer.userId(), null);
		lead.assignTo(owner);
		lead.scheduleFollowUp(request.nextFollowUpAt());
		linkExistingAccount(lead);
		leads.saveAndFlush(lead);
		log(lead, ActivityType.SYSTEM, "Lead created by staff", viewer.userId());
		if (lead.getUserId() != null) {
			log(lead, ActivityType.SYSTEM, "Linked to an existing investor account", null);
		}
		audit.record(AuditRecord.of(viewer.userId(), AuditAction.LEAD_CREATED, ENTITY, lead.getId())
			.withNewValue(Map.of("reference", lead.getReference(), "source", LeadSource.STAFF)));
		publish(KafkaTopics.LEAD_CREATED, "LeadCreated", lead);
		return detail(viewer, lead.getId());
	}

	@Transactional
	public LeadDetail update(Viewer viewer, UUID leadId, LeadRequest request) {
		Lead lead = lockChangeable(viewer, leadId);
		Lead.Contact contact = contact(request);
		if (contact.email() != null && !contact.email().equals(lead.getEmail()) && lead.getStage().isOpen()) {
			leads.lockOpenByEmail(contact.email()).ifPresent(existing -> {
				throw new BusinessException(ErrorCode.CONFLICT,
						"An open lead for this email already exists (" + existing.getReference() + ")");
			});
		}
		lead.update(contact);
		lead.scheduleFollowUp(request.nextFollowUpAt());
		linkExistingAccount(lead);
		leads.flush();
		return detail(viewer, leadId);
	}

	/** Moves a lead through the pipeline. LOST needs a reason; WON is final; LOST can be reopened. */
	@Transactional
	public LeadDetail changeStage(Viewer viewer, UUID leadId, LeadStage target, String reason) {
		Lead lead = lockChangeable(viewer, leadId);
		LeadStage from = lead.getStage();
		if (from == target) {
			throw new BusinessException(ErrorCode.CONFLICT, "The lead is already " + target);
		}
		if (from == LeadStage.WON) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A won lead is final");
		}
		String why = reason == null || reason.isBlank() ? null : reason.strip();
		if (target == LeadStage.LOST && why == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Say why the lead was lost");
		}
		if (from == LeadStage.LOST && lead.getEmail() != null) {
			// Reopening must not create a second open lead for the same email.
			leads.lockOpenByEmail(lead.getEmail()).ifPresent(existing -> {
				throw new BusinessException(ErrorCode.CONFLICT,
						"Another open lead exists for this email (" + existing.getReference() + ")");
			});
		}
		lead.moveTo(target, why, clock.instant());
		leads.flush();
		log(lead, ActivityType.STAGE_CHANGE, label(from) + " → " + label(target) + (why == null ? "" : ": " + why),
				viewer.userId());
		publish(KafkaTopics.LEAD_UPDATED, "LeadStageChanged", lead);
		return detail(viewer, leadId);
	}

	@Transactional
	public LeadDetail logActivity(Viewer viewer, UUID leadId, ActivityRequest request) {
		if (request.type() == ActivityType.STAGE_CHANGE || request.type() == ActivityType.ASSIGNMENT
				|| request.type() == ActivityType.SYSTEM) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Log a note, call, email or meeting");
		}
		Lead lead = lockChangeable(viewer, leadId);
		log(lead, request.type(), request.body().strip(), viewer.userId());
		leads.flush();
		return detail(viewer, leadId);
	}

	/** A rep takes an unassigned lead. */
	@Transactional
	public LeadDetail claim(Viewer viewer, UUID leadId) {
		Lead lead = lockVisible(viewer, leadId);
		if (lead.getOwnerId() != null) {
			throw new BusinessException(ErrorCode.CONFLICT, viewer.userId().equals(lead.getOwnerId())
					? "This lead is already yours" : "This lead has already been taken");
		}
		lead.assignTo(viewer.userId());
		leads.flush();
		log(lead, ActivityType.ASSIGNMENT, "Claimed by " + name(viewer.userId()), viewer.userId());
		publish(KafkaTopics.LEAD_UPDATED, "LeadAssigned", lead);
		return detail(viewer, leadId);
	}

	/** A manager (re)assigns a lead, or returns it to the unassigned pool. */
	@Transactional
	public LeadDetail assign(Viewer viewer, UUID leadId, UUID ownerId) {
		if (!viewer.manager()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Only sales managers assign leads");
		}
		Lead lead = lockVisible(viewer, leadId);
		if (Objects.equals(lead.getOwnerId(), ownerId)) {
			throw new BusinessException(ErrorCode.CONFLICT, "The lead is already assigned that way");
		}
		if (ownerId != null) {
			requireCanWorkLeads(ownerId);
		}
		UUID previous = lead.getOwnerId();
		lead.assignTo(ownerId);
		leads.flush();
		log(lead, ActivityType.ASSIGNMENT, ownerId == null ? "Returned to the unassigned pool"
				: "Assigned to " + name(ownerId), viewer.userId());
		audit.record(AuditRecord.of(viewer.userId(), AuditAction.LEAD_ASSIGNED, ENTITY, leadId)
			.withOldValue(previous == null ? null : Map.of("ownerId", previous.toString()))
			.withNewValue(ownerId == null ? null : Map.of("ownerId", ownerId.toString())));
		publish(KafkaTopics.LEAD_UPDATED, "LeadAssigned", lead);
		return detail(viewer, leadId);
	}

	// ----------------------------------------------------------------------------- reads

	@Transactional(readOnly = true)
	public Page<LeadResponse> search(Viewer viewer, LeadSearchCriteria criteria, Pageable pageable) {
		Page<Lead> page = leads.findAll(matching(viewer, criteria), pageable);
		Map<UUID, String> owners = names(page.stream().map(Lead::getOwnerId));
		return page.map(l -> LeadResponse.of(l, owners.get(l.getOwnerId())));
	}

	@Transactional(readOnly = true)
	public LeadDetail detail(Viewer viewer, UUID leadId) {
		Lead lead = leads.findById(leadId).filter(viewer::sees)
			.orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
		List<LeadActivity> history = activities.findByLeadIdOrderByCreatedAtDescIdDesc(leadId, PageRequest.of(0, 100));
		Map<UUID, String> people = names(Stream.concat(Stream.of(lead.getOwnerId()), history.stream().map(LeadActivity::getActorId)));
		return new LeadDetail(LeadResponse.of(lead, people.get(lead.getOwnerId())), viewer.mayChange(lead),
				history.stream()
					.map(a -> new LeadDetail.Activity(a.getId(), a.getType(), a.getBody(), people.get(a.getActorId()),
							a.getCreatedAt()))
					.toList());
	}

	/** Lead counts and value per stage, within what the viewer can see. */
	@Transactional(readOnly = true)
	public List<PipelineStage> pipeline(Viewer viewer, String owner) {
		Map<LeadStage, Long> counts = new EnumMap<>(LeadStage.class);
		Map<LeadStage, Map<String, Money>> values = new EnumMap<>(LeadStage.class);
		for (Lead lead : leads.findAll(matching(viewer, new LeadSearchCriteria(null, null, null, owner, false)))) {
			counts.merge(lead.getStage(), 1L, Long::sum);
			Money value = lead.getStage() == LeadStage.WON ? lead.won() : lead.contact().estimate();
			if (value != null && lead.getStage() != LeadStage.LOST) {
				values.computeIfAbsent(lead.getStage(), s -> new TreeMap<>())
					.merge(value.currency().getCurrencyCode(), value, Money::plus);
			}
		}
		List<PipelineStage> stages = new ArrayList<>();
		for (LeadStage stage : LeadStage.values()) {
			stages.add(new PipelineStage(stage, counts.getOrDefault(stage, 0L),
					values.getOrDefault(stage, Map.of()).values().stream().map(MoneyResponse::from).toList()));
		}
		return stages;
	}

	/** Staff who can be given leads: everyone holding LEAD_UPDATE. */
	@Transactional(readOnly = true)
	public List<Assignee> assignees() {
		return accounts.getAccounts(roles.usersWithPermission(PermissionCode.LEAD_UPDATE.name())).values().stream()
			.map(a -> new Assignee(a.id(), a.firstName() + " " + a.lastName(), a.email()))
			.sorted(Comparator.comparing(Assignee::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	// ----------------------------------------------------------------------------- internal

	private Lead.Contact contact(LeadRequest r) {
		String email = r.email() == null || r.email().isBlank() ? null : EmailNormalizer.normalize(r.email());
		String phone = r.phone() == null || r.phone().isBlank() ? null : r.phone().strip();
		if (email == null && phone == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give an email address or a phone number");
		}
		if ((r.estimatedAmount() == null) != (r.estimatedCurrency() == null || r.estimatedCurrency().isBlank())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give the estimated amount and its currency together");
		}
		Money estimate = r.estimatedAmount() == null ? null
				: Money.of(r.estimatedAmount(), Currency.getInstance(r.estimatedCurrency()));
		return new Lead.Contact(r.firstName().strip(), blankToNull(r.lastName()), email, phone,
				blankToNull(r.country()), r.interest(), estimate);
	}

	/** A lead whose email belongs to an existing account is linked to it straight away. */
	void linkExistingAccount(Lead lead) {
		if (lead.getUserId() == null && lead.getEmail() != null) {
			accounts.findByEmail(lead.getEmail()).ifPresent(account -> {
				lead.linkAccount(account.id());
				if (lead.getId() != null) {
					log(lead, ActivityType.SYSTEM, "Linked to an existing investor account", null);
				}
			});
		}
	}

	private void requireCanWorkLeads(UUID userId) {
		if (!roles.usersWithPermission(PermissionCode.LEAD_UPDATE.name()).contains(userId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "That user cannot work leads");
		}
	}

	private Lead lockVisible(Viewer viewer, UUID leadId) {
		return leads.findByIdForUpdate(leadId).filter(viewer::sees)
			.orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
	}

	private Lead lockChangeable(Viewer viewer, UUID leadId) {
		Lead lead = lockVisible(viewer, leadId);
		if (!viewer.mayChange(lead)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Claim this lead before working on it");
		}
		return lead;
	}

	/** Package-private for the website form and conversion, which write platform entries. */
	void log(Lead lead, ActivityType type, String body, UUID actorId) {
		activities.save(new LeadActivity(lead.getId(), type, body, actorId, clock.instant()));
	}

	/** Kafka payloads carry no contact details: consumers look the lead up if they need them. */
	void publish(String topic, String type, Lead lead) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("leadId", lead.getId().toString());
		payload.put("reference", lead.getReference());
		payload.put("stage", lead.getStage().name());
		payload.put("source", lead.getSource().name());
		if (lead.getOwnerId() != null) {
			payload.put("ownerId", lead.getOwnerId().toString());
		}
		if (lead.getUserId() != null) {
			payload.put("userId", lead.getUserId().toString());
		}
		outbox.publish(DomainEvent.of(topic, type, ENTITY, lead.getId(), payload));
	}

	private String name(UUID userId) {
		UserAccount a = accounts.getAccount(userId);
		return a.firstName() + " " + a.lastName();
	}

	private Map<UUID, String> names(Stream<UUID> ids) {
		Set<UUID> wanted = new HashSet<>();
		ids.filter(Objects::nonNull).forEach(wanted::add);
		Map<UUID, String> names = new HashMap<>();
		accounts.getAccounts(wanted).forEach((id, a) -> names.put(id, a.firstName() + " " + a.lastName()));
		return names;
	}

	private static String label(LeadStage stage) {
		String s = stage.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private Specification<Lead> matching(Viewer viewer, LeadSearchCriteria c) {
		Instant now = clock.instant();
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (!viewer.manager()) {
				predicates.add(cb.or(cb.isNull(root.get("ownerId")), cb.equal(root.get("ownerId"), viewer.userId())));
			}
			if ("me".equals(c.owner())) {
				predicates.add(cb.equal(root.get("ownerId"), viewer.userId()));
			}
			else if ("unassigned".equals(c.owner())) {
				predicates.add(cb.isNull(root.get("ownerId")));
			}
			else if (c.owner() != null && !c.owner().isBlank() && viewer.manager()) {
				try {
					predicates.add(cb.equal(root.get("ownerId"), UUID.fromString(c.owner())));
				}
				catch (IllegalArgumentException ex) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, "owner must be me, unassigned or a user id");
				}
			}
			if (c.stage() != null) {
				predicates.add(cb.equal(root.get("stage"), c.stage()));
			}
			if (c.source() != null) {
				predicates.add(cb.equal(root.get("source"), c.source()));
			}
			if (c.due()) {
				predicates.add(cb.lessThanOrEqualTo(root.get("nextFollowUpAt"), now));
				predicates.add(root.get("stage").in(LeadStage.NEW, LeadStage.CONTACTED, LeadStage.QUALIFIED,
						LeadStage.PROPOSAL));
			}
			if (c.q() != null && !c.q().isBlank()) {
				String like = "%" + c.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("reference")), like, '\\'),
						cb.like(cb.lower(root.get("firstName")), like, '\\'),
						cb.like(cb.lower(root.get("lastName")), like, '\\'),
						cb.like(root.get("email"), like, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
