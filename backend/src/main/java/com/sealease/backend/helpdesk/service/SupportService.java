package com.sealease.backend.helpdesk.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.document.service.DocumentService;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.helpdesk.automation.AutomationEngine;
import com.sealease.backend.helpdesk.dto.Agent;
import com.sealease.backend.helpdesk.dto.BulkAssignRequest;
import com.sealease.backend.helpdesk.dto.StaffTicketRequest;
import com.sealease.backend.helpdesk.entity.AssignmentStrategy;
import com.sealease.backend.helpdesk.entity.AutomationTrigger;
import com.sealease.backend.helpdesk.entity.EscalationTrigger;
import com.sealease.backend.helpdesk.entity.SlaPolicy;
import com.sealease.backend.helpdesk.entity.SupportCategory;
import com.sealease.backend.helpdesk.entity.TicketChannel;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.repository.SlaPolicyRepository;
import com.sealease.backend.helpdesk.repository.SupportCategoryRepository;
import com.sealease.backend.helpdesk.sla.SlaService;
import com.sealease.backend.helpdesk.dto.OpenTicketRequest;
import com.sealease.backend.helpdesk.dto.TicketDetail;
import com.sealease.backend.helpdesk.dto.TicketResponse;
import com.sealease.backend.helpdesk.dto.TicketSearchCriteria;
import com.sealease.backend.helpdesk.entity.RelatedType;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketAttachment;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketEvent;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.helpdesk.entity.TicketMessage;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import com.sealease.backend.helpdesk.repository.SupportTicketRepository;
import com.sealease.backend.helpdesk.repository.TicketAttachmentRepository;
import com.sealease.backend.helpdesk.repository.TicketEventRepository;
import com.sealease.backend.helpdesk.repository.TicketMessageRepository;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Support tickets: investors open, reply, close and reopen; agents (SUPPORT_TICKET_MANAGE) reply, add internal
 * notes, prioritise, assign, resolve and close. Every change is kept as a ticket event, so investors can follow
 * their request. Investors only ever see their own tickets, and never internal notes, the attachments on them,
 * priority changes or unassignments.
 */
@Service
public class SupportService {

	private static final String ENTITY = "SUPPORT_TICKET";
	private static final int MAX_FILES = 3;

	private final SupportTicketRepository tickets;
	private final TicketMessageRepository messages;
	private final TicketAttachmentRepository attachments;
	private final TicketEventRepository events;
	private final DocumentService documents;
	private final NotificationService notifications;
	private final OrderService orders;
	private final WithdrawalService withdrawals;
	private final HoldingService holdings;
	private final UserAccountService accounts;
	private final UserRoleService roles;
	private final AuditService audit;
	private final SupportProperties properties;
	private final TransactionTemplate transactions;
	private final SlaService sla;
	private final SlaPolicyRepository slaPolicies;
	private final AssignmentService assignment;
	private final EscalationService escalations;
	private final AutomationEngine automation;
	private final SupportCategoryRepository categories;
	private final JdbcTemplate jdbc;
	private final DisplayTimeZone display;
	private final Clock clock;

	public SupportService(SupportTicketRepository tickets, TicketMessageRepository messages,
			TicketAttachmentRepository attachments, TicketEventRepository events, DocumentService documents, NotificationService notifications,
			OrderService orders, WithdrawalService withdrawals, HoldingService holdings, UserAccountService accounts,
			UserRoleService roles, AuditService audit, SupportProperties properties, TransactionTemplate transactions,
			SlaService sla, SlaPolicyRepository slaPolicies, AssignmentService assignment, EscalationService escalations,
			AutomationEngine automation, SupportCategoryRepository categories, JdbcTemplate jdbc, DisplayTimeZone display,
			Clock clock) {
		this.transactions = transactions;
		this.sla = sla;
		this.slaPolicies = slaPolicies;
		this.assignment = assignment;
		this.escalations = escalations;
		this.automation = automation;
		this.categories = categories;
		this.jdbc = jdbc;
		this.display = display;
		this.tickets = tickets;
		this.messages = messages;
		this.attachments = attachments;
		this.events = events;
		this.documents = documents;
		this.notifications = notifications;
		this.orders = orders;
		this.withdrawals = withdrawals;
		this.holdings = holdings;
		this.accounts = accounts;
		this.roles = roles;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	// --------------------------------------------------------------------------- customer

	@Transactional
	public TicketDetail open(UUID userId, OpenTicketRequest request, List<MultipartFile> files) {
		requireFileCount(files);
		String relatedLabel = null;
		if ((request.relatedType() == null) != (request.relatedId() == null)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give both the related record's type and id");
		}
		if (request.relatedType() != null) {
			relatedLabel = relatedLabel(userId, request.relatedType(), request.relatedId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
						"The linked record was not found among yours"));
		}
		Instant now = clock.instant();
		SupportTicket ticket = create(userId, request.subject().strip(), request.category(), request.subcategoryId(),
				request.relatedType(), request.relatedId(), relatedLabel, null, TicketChannel.PORTAL, null, now);
		post(ticket, userId, false, false, request.message(), files, now);
		event(ticket, userId, false, TicketEventType.OPENED, null, TicketStatus.NEW, null, now);
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_OPENED, ENTITY, ticket.getId())
			.withNewValue(Map.of("reference", ticket.getReference(), "category", ticket.getCategory())));
		created(ticket);
		return customerDetail(userId, ticket.getId());
	}

	/**
	 * A ticket staff open for a customer (phone call, email). The message is public and the customer is told.
	 */
	@Transactional
	public TicketDetail openForCustomer(UUID actorId, StaffTicketRequest request) {
		UserAccount customer = accounts.getAccounts(Set.of(request.customerId())).get(request.customerId());
		if (customer == null) {
			throw new ResourceNotFoundException("User", request.customerId());
		}
		if (request.customerId().equals(actorId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You cannot open a ticket for yourself here");
		}
		Instant now = clock.instant();
		SupportTicket ticket = create(request.customerId(), request.subject().strip(), request.category(),
				request.subcategoryId(), null, null, null, request.priority(), TicketChannel.STAFF, actorId, now);
		post(ticket, actorId, true, false, request.message(), List.of(), now);
		event(ticket, actorId, true, TicketEventType.OPENED, null, ticket.getStatus(), null, now);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_OPENED, ENTITY, ticket.getId())
			.withNewValue(Map.of("reference", ticket.getReference(), "category", ticket.getCategory(),
					"onBehalfOf", request.customerId().toString(), "channel", TicketChannel.STAFF)));
		notifications.notify(request.customerId(), "TICKET_REPLY", ticket.getSubject(),
				"We opened " + ticket.getReference() + " for you.", "/support/" + ticket.getId());
		if (request.assigneeId() != null) {
			assignInternal(ticket, actorId, request.assigneeId(), now);
		}
		created(ticket);
		return staffDetail(ticket.getId());
	}

	/** A new ticket with its reference, category defaults and SLA targets. */
	private SupportTicket create(UUID customerId, String subject, TicketCategory category, UUID subcategoryId,
			RelatedType relatedType, UUID relatedId, String relatedLabel, TicketPriority requested, TicketChannel channel,
			UUID createdBy, Instant now) {
		SupportCategory sub = subcategoryId == null ? null : categories.findById(subcategoryId)
			.filter(c -> !c.isTopLevel() && c.isActive() && c.getCategory() == category)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a subcategory of " + category));
		SupportCategory top = categories.findByParentIdIsNullAndCategory(category).orElse(null);
		TicketPriority priority = requested != null ? requested
				: sub != null && sub.getDefaultPriority() != null ? sub.getDefaultPriority()
				: top != null && top.getDefaultPriority() != null ? top.getDefaultPriority()
				: TicketPriority.NORMAL;
		SupportTeam team = sub != null && sub.getDefaultTeam() != null ? sub.getDefaultTeam()
				: top != null ? top.getDefaultTeam() : null;
		SupportTicket ticket = new SupportTicket(nextReference(now), customerId, subject, category, relatedType, relatedId,
				relatedLabel, priority, now.plus(properties.responseTargets().of(priority)), now);
		ticket.origin(channel, createdBy, subcategoryId, team);
		sla.applyTargets(ticket, now);
		return tickets.saveAndFlush(ticket);
	}

	/** After a ticket is created: automatic assignment by its category, then the TICKET_CREATED rules. */
	private void created(SupportTicket ticket) {
		if (ticket.getAssigneeId() == null) {
			assignment.autoAssign(ticket, AssignmentStrategy.MANUAL);
			tickets.flush();
		}
		automation.fire(AutomationTrigger.TICKET_CREATED, ticket.getId(), "created");
	}

	/**
	 * SUP-yyyy-nnnnnn, numbered per year (display time zone). The counter row is updated atomically, so concurrent
	 * tickets never share a number.
	 */
	private String nextReference(Instant now) {
		int year = now.atZone(display.zone()).getYear();
		Long number = jdbc.queryForObject("""
				INSERT INTO support_ticket_counters (year, last_number) VALUES (?, 1)
				ON CONFLICT (year) DO UPDATE SET last_number = support_ticket_counters.last_number + 1
				RETURNING last_number""", Long.class, year);
		return "SUP-%d-%06d".formatted(year, number);
	}

	/**
	 * The customer writes. A resolved ticket reopens; a ticket waiting on them goes back in the queue (and its
	 * resolution clock restarts); staff then owe a next response. A closed ticket must be reopened first.
	 */
	@Transactional
	public TicketDetail customerReply(UUID userId, UUID ticketId, String body, List<MultipartFile> files) {
		requireFileCount(files);
		SupportTicket ticket = lockOwn(userId, ticketId);
		requireReopenable(ticket);
		Instant now = clock.instant();
		TicketStatus previous = ticket.getStatus();
		post(ticket, userId, false, false, body, files, now);
		ticket.customerReplied(now, sla.nextResponseDue(ticket, now));
		slaClock(ticket, previous, now);
		tickets.flush();
		if (previous == TicketStatus.RESOLVED) {
			event(ticket, userId, false, TicketEventType.REOPENED, previous, TicketStatus.OPEN, null, now);
			audit.record(AuditRecord.of(userId, AuditAction.TICKET_REOPENED, ENTITY, ticketId)
				.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", TicketStatus.OPEN, "byReply", true)));
			automation.fire(AutomationTrigger.CUSTOMER_REPLIED_RESOLVED, ticketId, "reply:" + now.toEpochMilli());
		}
		else if (previous != ticket.getStatus()) {
			event(ticket, userId, false, TicketEventType.STATUS_CHANGED, previous, ticket.getStatus(), null, now);
		}
		if (ticket.getAssigneeId() != null) {
			notifications.notify(ticket.getAssigneeId(), "TICKET_CUSTOMER_REPLY", "New reply on " + ticket.getReference(),
					ticket.getSubject(), "/platform/support/" + ticket.getId());
		}
		return customerDetail(userId, ticketId);
	}

	@Transactional
	public TicketDetail customerClose(UUID userId, UUID ticketId) {
		SupportTicket ticket = lockOwn(userId, ticketId);
		requireNotClosed(ticket);
		TicketStatus previous = ticket.getStatus();
		Instant now = clock.instant();
		ticket.close(now);
		tickets.flush();
		event(ticket, userId, false, TicketEventType.STATUS_CHANGED, previous, TicketStatus.CLOSED, null, now);
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_STATUS_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", TicketStatus.CLOSED)));
		automation.fire(AutomationTrigger.TICKET_CLOSED, ticketId, "closed");
		return customerDetail(userId, ticketId);
	}

	/**
	 * Reopens a resolved or closed ticket, within the reopen window after it was resolved or closed. The reason is
	 * posted to the conversation and the ticket goes back in the queue, to its assignee if it has one.
	 */
	@Transactional
	public TicketDetail reopen(UUID userId, UUID ticketId, String reason) {
		SupportTicket ticket = lockOwn(userId, ticketId);
		TicketStatus previous = ticket.getStatus();
		requireReopenable(ticket);
		if (previous != TicketStatus.RESOLVED) {
			throw new BusinessException(ErrorCode.CONFLICT, "Only a resolved request can be reopened");
		}
		Instant now = clock.instant();
		post(ticket, userId, false, false, reason, List.of(), now);
		ticket.reopen(now, sla.nextResponseDue(ticket, now));
		tickets.flush();
		event(ticket, userId, false, TicketEventType.REOPENED, previous, TicketStatus.OPEN, null, now);
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_REOPENED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", TicketStatus.OPEN)));
		if (ticket.getAssigneeId() != null) {
			notifications.notify(ticket.getAssigneeId(), "TICKET_REOPENED", ticket.getReference() + " was reopened",
					ticket.getSubject(), "/platform/support/" + ticket.getId());
		}
		automation.fire(AutomationTrigger.TICKET_REOPENED, ticketId, "reopen:" + ticket.getReopenCount());
		return customerDetail(userId, ticketId);
	}

	@Transactional(readOnly = true)
	public Page<TicketResponse> mine(UUID userId, Pageable pageable) {
		Instant now = clock.instant();
		Map<UUID, String> subcategories = subcategoryNames();
		return tickets.findByUserIdOrderByLastMessageAtDesc(userId, pageable)
			.map(t -> TicketResponse.forCustomer(t, subcategories.get(t.getSubcategoryId()), reopenableUntil(t, now)));
	}

	@Transactional(readOnly = true)
	public TicketDetail customerDetail(UUID userId, UUID ticketId) {
		SupportTicket ticket = tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		return detail(ticket, TicketResponse.forCustomer(ticket, subcategoryNames().get(ticket.getSubcategoryId()),
				reopenableUntil(ticket)), false);
	}

	/** An attachment the customer may see: on their own ticket and not on an internal note. */
	@Transactional(readOnly = true)
	public DocumentContent customerAttachment(UUID userId, UUID ticketId, UUID attachmentId) {
		SupportTicket ticket = tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		TicketAttachment attachment = attachment(ticket, attachmentId);
		boolean internal = messages.findById(attachment.getMessageId()).map(TicketMessage::isInternal).orElse(true);
		if (internal) {
			throw new ResourceNotFoundException("Attachment", attachmentId);
		}
		return documents.load(attachment.getDocumentId());
	}

	// ------------------------------------------------------------------------------ staff

	/**
	 * A ticket staff (or the system, on a staff member's action) opens on a customer's behalf about one of their
	 * records, e.g. a withdrawal the bank did not pay. The opening message is public: the customer is told, and can
	 * reply. Assigned to {@code assigneeId} when that user can handle tickets. Caller's transaction.
	 *
	 * @return the ticket's id
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID openOnBehalf(UUID actorId, UUID customerId, TicketCategory category, RelatedType relatedType,
			UUID relatedId, String relatedLabel, String subject, String message, TicketPriority priority, UUID assigneeId) {
		Instant now = clock.instant();
		SupportTicket ticket = create(customerId, subject, category, null, relatedType, relatedId, relatedLabel, priority,
				TicketChannel.SYSTEM, actorId, now);
		post(ticket, actorId, true, false, message, List.of(), now);
		event(ticket, actorId, true, TicketEventType.OPENED, null, ticket.getStatus(), null, now);
		if (assigneeId != null && !assigneeId.equals(customerId)
				&& roles.usersWithPermission(PermissionCode.SUPPORT_TICKET_MANAGE.name()).contains(assigneeId)) {
			ticket.assignTo(assigneeId);
			tickets.flush();
			event(ticket, actorId, true, TicketEventType.ASSIGNED, null, null, assigneeId, now);
		}
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_OPENED, ENTITY, ticket.getId())
			.withNewValue(Map.of("reference", ticket.getReference(), "category", category, "onBehalfOf", customerId.toString())));
		notifications.notify(customerId, "TICKET_REPLY", subject, "We opened " + ticket.getReference() + " to sort this out.",
				"/support/" + ticket.getId());
		created(ticket);
		return ticket.getId();
	}

	/**
	 * Tells the customer how a ticket opened on their behalf was settled and marks it resolved (closed tickets are
	 * left alone). Caller's transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void resolveWithNote(UUID actorId, UUID ticketId, String note) {
		SupportTicket ticket = lock(ticketId);
		if (ticket.getStatus() == TicketStatus.CLOSED) {
			return;
		}
		Instant now = clock.instant();
		post(ticket, actorId, true, false, note, List.of(), now);
		TicketStatus previous = ticket.getStatus();
		if (previous != TicketStatus.RESOLVED) {
			ticket.resolve(now);
			slaClock(ticket, previous, now);
			event(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previous, TicketStatus.RESOLVED, null, now);
			automation.fire(AutomationTrigger.TICKET_RESOLVED, ticketId, "resolved:" + now.toEpochMilli());
		}
		tickets.flush();
		notifications.notify(ticket.getUserId(), "TICKET_STATUS", ticket.getReference() + " was resolved", note,
				"/support/" + ticket.getId());
	}

	/**
	 * As {@link #resolveWithNote}, but only while the ticket is still open: for a note with nothing new to say once the
	 * ticket is resolved (an investor submitting their documents again after a rejection). Caller's transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void resolveIfOpen(UUID actorId, UUID ticketId, String note) {
		TicketStatus status = lock(ticketId).getStatus();
		if (status != TicketStatus.RESOLVED && status != TicketStatus.CLOSED) {
			resolveWithNote(actorId, ticketId, note);
		}
	}

	/** A public reply puts the ticket in the customer's court and notifies them; an internal note changes nothing. */
	@Transactional
	public TicketDetail staffReply(UUID actorId, boolean financeOnly, UUID ticketId, String body, boolean internal,
			List<MultipartFile> files) {
		requireFileCount(files);
		SupportTicket ticket = lock(ticketId);
		requireWorkingOn(ticket, actorId, financeOnly);
		requireNotClosed(ticket);
		if (ticket.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot answer your own ticket");
		}
		Instant now = clock.instant();
		TicketStatus previous = ticket.getStatus();
		post(ticket, actorId, true, internal, body, files, now);
		if (!internal) {
			ticket.staffReplied(now);
			slaClock(ticket, previous, now);
			if (previous != TicketStatus.WAITING_ON_CUSTOMER) {
				event(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previous, TicketStatus.WAITING_ON_CUSTOMER, null, now);
			}
			notifications.notify(ticket.getUserId(), "TICKET_REPLY", "Support replied to " + ticket.getReference(),
					ticket.getSubject(), "/support/" + ticket.getId());
		}
		tickets.flush();
		audit.record(AuditRecord.of(actorId, internal ? AuditAction.TICKET_NOTE_ADDED : AuditAction.TICKET_REPLIED, ENTITY, ticketId)
			.withNewValue(Map.of("attachments", files == null ? 0 : files.stream().filter(f -> f != null && !f.isEmpty()).count())));
		return staffDetail(ticketId);
	}

	/**
	 * Moves a ticket along its lifecycle. Staff set IN_PROGRESS, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, RESOLVED or
	 * CLOSED, following the allowed transitions ({@link TicketStatus#staffMayMoveTo}); NEW, OPEN, ESCALATED and
	 * REOPENED follow from replies, escalation and reopening.
	 */
	@Transactional
	public TicketDetail setStatus(UUID actorId, boolean financeOnly, UUID ticketId, TicketStatus target) {
		if (!TicketStatus.STAFF_SETTABLE.contains(target)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Staff can set In progress, Waiting on customer, Waiting on internal team, Resolved or Closed; replies reopen tickets");
		}
		SupportTicket ticket = lock(ticketId);
		requireWorkingOn(ticket, actorId, financeOnly);
		requireNotClosed(ticket);
		TicketStatus previous = ticket.getStatus();
		if (previous == target) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already " + target);
		}
		if (!previous.staffMayMoveTo(target)) {
			throw new BusinessException(ErrorCode.CONFLICT, "A " + previous + " ticket cannot move to " + target);
		}
		Instant now = clock.instant();
		ticket.moveTo(target, now);
		slaClock(ticket, previous, now);
		tickets.flush();
		event(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previous, target, null, now);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_STATUS_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", target)));
		if (target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED) {
			notifications.notify(ticket.getUserId(), "TICKET_STATUS", ticket.getReference() + " was "
					+ target.name().toLowerCase(Locale.ROOT), ticket.getSubject(), "/support/" + ticket.getId());
		}
		if (target == TicketStatus.RESOLVED) {
			automation.fire(AutomationTrigger.TICKET_RESOLVED, ticketId, "resolved:" + now.toEpochMilli());
		}
		else if (target == TicketStatus.CLOSED) {
			automation.fire(AutomationTrigger.TICKET_CLOSED, ticketId, "closed");
		}
		return staffDetail(ticketId);
	}

	/** Stops or restarts the resolution clock after a status change, recording it in the (staff-only) history. */
	private void slaClock(SupportTicket ticket, TicketStatus previous, Instant now) {
		sla.statusChanged(ticket, previous, now).ifPresent(change ->
				event(ticket, null, true, TicketEventType.valueOf(change), null, null, null, now));
	}

	/** Changing priority moves the first-response target, unless staff have already responded. */
	@Transactional
	public TicketDetail setPriority(UUID actorId, boolean financeOnly, UUID ticketId, TicketPriority priority) {
		SupportTicket ticket = lock(ticketId);
		requireWorkingOn(ticket, actorId, financeOnly);
		TicketPriority previous = ticket.getPriority();
		if (previous == priority) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already " + priority);
		}
		ticket.reprioritise(priority, ticket.getFirstResponseDueAt());
		sla.applyTargets(ticket, ticket.getCreatedAt());
		tickets.flush();
		event(ticket, actorId, true, TicketEventType.PRIORITY_CHANGED, previous, priority, null, clock.instant());
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_PRIORITY_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("priority", previous)).withNewValue(Map.of("priority", priority)));
		return staffDetail(ticketId);
	}

	/**
	 * Assigns a ticket, or unassigns it. Support assigns any ticket to anyone who handles tickets (finance members only
	 * for tickets handed to finance). Finance only takes a ticket handed to it for themselves, or gives back their own.
	 */
	@Transactional
	public TicketDetail assign(UUID actorId, boolean financeOnly, UUID ticketId, UUID assigneeId) {
		SupportTicket ticket = lock(ticketId);
		if (financeOnly) {
			requireVisible(ticket, true);
			boolean takes = actorId.equals(assigneeId);
			boolean givesBack = assigneeId == null && actorId.equals(ticket.getAssigneeId());
			if (!takes && !givesBack) {
				throw new BusinessException(ErrorCode.FORBIDDEN, "You can only assign a ticket to yourself");
			}
			if (takes && ticket.getAssigneeId() != null && !ticket.getAssigneeId().equals(actorId)) {
				throw new BusinessException(ErrorCode.CONFLICT, "Another finance member is already working on this ticket");
			}
		}
		if (Objects.equals(ticket.getAssigneeId(), assigneeId)) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already assigned that way");
		}
		if (assigneeId != null) {
			if (!handlers(ticket.getTeam()).contains(assigneeId)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "That user cannot handle tickets");
			}
			if (assigneeId.equals(ticket.getUserId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A ticket cannot be assigned to its requester");
			}
		}
		Instant now = clock.instant();
		ticket.assignTo(assigneeId);
		if (assigneeId != null) {
			ticket.acknowledge(now);
		}
		tickets.flush();
		event(ticket, actorId, true, assigneeId == null ? TicketEventType.UNASSIGNED : TicketEventType.ASSIGNED, null, null,
				assigneeId, now);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ASSIGNED, ENTITY, ticketId)
			.withNewValue(assigneeId == null ? null : Map.of("assigneeId", assigneeId.toString())));
		if (assigneeId != null && !assigneeId.equals(actorId)) {
			notifications.notify(assigneeId, "TICKET_ASSIGNED", ticket.getReference() + " was assigned to you",
					ticket.getSubject(), "/platform/support/" + ticket.getId());
		}
		return staffDetail(ticketId);
	}

	/** Assigns a locked ticket to an agent who can handle it (used when staff open a ticket and in bulk). */
	private void assignInternal(SupportTicket ticket, UUID actorId, UUID assigneeId, Instant now) {
		if (!assignment.handlers(ticket.getTeam()).contains(assigneeId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "That user cannot handle tickets");
		}
		if (assigneeId.equals(ticket.getUserId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A ticket cannot be assigned to its requester");
		}
		ticket.assignTo(assigneeId);
		ticket.acknowledge(now);
		tickets.flush();
		event(ticket, actorId, true, TicketEventType.ASSIGNED, null, null, assigneeId, now);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ASSIGNED, ENTITY, ticket.getId())
			.withNewValue(Map.of("assigneeId", assigneeId.toString())));
		if (!assigneeId.equals(actorId)) {
			notifications.notify(assigneeId, "TICKET_ASSIGNED", ticket.getReference() + " was assigned to you",
					ticket.getSubject(), "/platform/support/" + ticket.getId());
		}
	}

	/**
	 * Assigns several tickets to one agent, or spreads them by round-robin or least-loaded. Each ticket is assigned in
	 * its own transaction, so one that cannot be (closed, the requester) does not stop the others.
	 */
	public List<BulkAssignRequest.Result> bulkAssign(UUID actorId, BulkAssignRequest request) {
		AssignmentStrategy strategy = null;
		if (request.strategy() != null && !request.strategy().isBlank()) {
			try {
				strategy = AssignmentStrategy.valueOf(request.strategy());
			}
			catch (IllegalArgumentException ex) {
				strategy = AssignmentStrategy.MANUAL;
			}
			if (strategy == AssignmentStrategy.MANUAL) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "strategy must be ROUND_ROBIN or LEAST_LOADED");
			}
		}
		else if (request.assigneeId() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose an agent or an assignment strategy");
		}
		AssignmentStrategy chosen = strategy;
		List<BulkAssignRequest.Result> results = new ArrayList<>();
		for (UUID id : new java.util.LinkedHashSet<>(request.ticketIds())) {
			try {
				results.add(transactions.execute(s -> {
					SupportTicket ticket = lock(id);
					requireNotClosed(ticket);
					Instant now = clock.instant();
					if (chosen != null) {
						if (ticket.getAssigneeId() != null) {
							ticket.assignTo(null);
							tickets.flush();
						}
						UUID picked = assignment.pick(ticket, chosen)
							.orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "No agent is available"));
						jdbc.update("UPDATE support_assignment_rules SET last_assignee_id = ? WHERE category = ?", picked,
								ticket.getCategory().name());
						assignInternal(ticket, actorId, picked, now);
					}
					else if (!request.assigneeId().equals(ticket.getAssigneeId())) {
						assignInternal(ticket, actorId, request.assigneeId(), now);
					}
					return new BulkAssignRequest.Result(id, ticket.getReference(), true, ticket.getAssigneeId(), null);
				}));
			}
			catch (BusinessException ex) {
				results.add(new BulkAssignRequest.Result(id, null, false, null, ex.getMessage()));
			}
		}
		return results;
	}

	/** Moves a ticket to another category or subcategory; SLA targets follow the new category. */
	@Transactional
	public TicketDetail setCategory(UUID actorId, UUID ticketId, TicketCategory category, UUID subcategoryId) {
		SupportTicket ticket = lock(ticketId);
		requireNotClosed(ticket);
		if (subcategoryId != null) {
			categories.findById(subcategoryId).filter(c -> !c.isTopLevel() && c.isActive() && c.getCategory() == category)
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a subcategory of " + category));
		}
		TicketCategory previous = ticket.getCategory();
		UUID previousSub = ticket.getSubcategoryId();
		if (previous == category && Objects.equals(previousSub, subcategoryId)) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already in that category");
		}
		Instant now = clock.instant();
		ticket.recategorise(category, subcategoryId);
		sla.applyTargets(ticket, ticket.getCreatedAt());
		tickets.flush();
		event(ticket, actorId, true, TicketEventType.CATEGORY_CHANGED, previous, category, null, now);
		Map<String, Object> before = new java.util.HashMap<>();
		before.put("category", previous);
		before.put("subcategoryId", previousSub == null ? null : previousSub.toString());
		Map<String, Object> after = new java.util.HashMap<>();
		after.put("category", category);
		after.put("subcategoryId", subcategoryId == null ? null : subcategoryId.toString());
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_CATEGORY_CHANGED, ENTITY, ticketId)
			.withOldValue(before).withNewValue(after));
		return staffDetail(ticketId);
	}

	/** Escalates by hand, one level or to a chosen level, with a reason. */
	@Transactional
	public TicketDetail escalate(UUID actorId, boolean financeOnly, UUID ticketId, Integer level, String reason, UUID ownerId) {
		SupportTicket ticket = lock(ticketId);
		requireVisible(ticket, financeOnly);
		if (ownerId != null && !assignment.handlers(ticket.getTeam()).contains(ownerId)
				&& !escalations.contactIds(level == null ? ticket.getEscalationLevel() + 1 : level).contains(ownerId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The owner must handle tickets or be a contact for that level");
		}
		escalations.escalate(ticket, level, EscalationTrigger.MANUAL, reason, actorId, ownerId);
		tickets.flush();
		return staffDetail(ticketId);
	}

	@Transactional(readOnly = true)
	public Page<TicketResponse> search(UUID viewerId, boolean financeOnly, TicketSearchCriteria criteria, Pageable pageable) {
		Instant now = clock.instant();
		if (financeOnly) {
			criteria = criteria.withTeam(SupportTeam.FINANCE);
		}
		List<UUID> customers = criteria.q() == null || criteria.q().isBlank() ? List.of()
				: accounts.searchIds(criteria.q().strip(), 200);
		Page<SupportTicket> page = tickets.findAll(matching(viewerId, criteria, now, customers, display), pageable);
		Map<UUID, String> names = names(page.stream().flatMap(t -> Stream.of(t.getUserId(), t.getAssigneeId())));
		return page.map(t -> staffView(t, names, now));
	}

	private TicketResponse staffView(SupportTicket t, Map<UUID, String> names, Instant now) {
		Map<UUID, SlaPolicy> policies = policies();
		String state = sla.view(t, Optional.ofNullable(t.getSlaPolicyId()).map(policies::get), now).overall().name();
		return TicketResponse.forStaff(t, names.get(t.getUserId()), names.get(t.getAssigneeId()),
				subcategoryNames().get(t.getSubcategoryId()), state, now, reopenableUntil(t, now));
	}

	private Map<UUID, SlaPolicy> policies() {
		return slaPolicies.findAll().stream().collect(Collectors.toMap(SlaPolicy::getId, p -> p));
	}

	private Map<UUID, String> subcategoryNames() {
		return categories.findAll().stream().filter(c -> !c.isTopLevel())
			.collect(Collectors.toMap(SupportCategory::getId, SupportCategory::getName));
	}

	/** A ticket for staff; finance only sees the tickets handed to it. */
	@Transactional(readOnly = true)
	public TicketDetail staffDetail(boolean financeOnly, UUID ticketId) {
		SupportTicket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		requireVisible(ticket, financeOnly);
		return staffDetail(ticketId);
	}

	@Transactional(readOnly = true)
	public TicketDetail staffDetail(UUID ticketId) {
		SupportTicket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		Map<UUID, String> names = names(Stream.of(ticket.getUserId(), ticket.getAssigneeId()));
		return detail(ticket, staffView(ticket, names, clock.instant()), true);
	}

	/** Staff downloads are audited: attachments can hold personal financial documents. */
	@Transactional
	public DocumentContent staffAttachment(UUID actorId, boolean financeOnly, UUID ticketId, UUID attachmentId) {
		SupportTicket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		requireVisible(ticket, financeOnly);
		TicketAttachment attachment = attachment(ticket, attachmentId);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ATTACHMENT_VIEWED, ENTITY, ticketId)
			.withNewValue(Map.of("attachmentId", attachmentId.toString(), "filename", attachment.getFilename())));
		return documents.load(attachment.getDocumentId());
	}


	/**
	 * Hands a ticket to another team, unassigned. Support hands any ticket to finance (whose members are told) or
	 * back; finance only hands back to support a ticket it is working on.
	 */
	@Transactional
	public TicketDetail handTo(UUID actorId, boolean financeOnly, UUID ticketId, SupportTeam team) {
		SupportTicket ticket = lock(ticketId);
		if (financeOnly) {
			requireWorkingOn(ticket, actorId, true);
			if (team != SupportTeam.SUPPORT) {
				throw new BusinessException(ErrorCode.FORBIDDEN, "Finance can only hand a ticket back to support");
			}
		}
		requireNotClosed(ticket);
		SupportTeam previous = ticket.getTeam();
		if (previous == team) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already with " + team.name().toLowerCase(Locale.ROOT));
		}
		ticket.handTo(team);
		tickets.flush();
		event(ticket, actorId, true, TicketEventType.TEAM_CHANGED, previous, team, null, clock.instant());
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_TEAM_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("team", previous)).withNewValue(Map.of("team", team)));
		if (team == SupportTeam.FINANCE) {
			handlers(SupportTeam.FINANCE).stream()
				.filter(id -> !id.equals(actorId) && !id.equals(ticket.getUserId()))
				.forEach(id -> notifications.notify(id, "TICKET_HANDED_TO_FINANCE",
						ticket.getReference() + " was handed to finance", ticket.getSubject(), "/platform/support/" + ticket.getId()));
		}
		return staffDetail(ticketId);
	}

	private Set<UUID> handlers(SupportTeam team) {
		return assignment.handlers(team);
	}

	/** Finance only sees the tickets handed to it; anything else is not found, as if it did not exist. */
	static void requireVisible(SupportTicket ticket, boolean financeOnly) {
		if (financeOnly && ticket.getTeam() != SupportTeam.FINANCE) {
			throw new ResourceNotFoundException("Ticket", ticket.getId());
		}
	}

	/** Finance works only on a ticket handed to it that they have assigned to themselves. */
	private static void requireWorkingOn(SupportTicket ticket, UUID actorId, boolean financeOnly) {
		requireVisible(ticket, financeOnly);
		if (financeOnly && !actorId.equals(ticket.getAssigneeId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, ticket.getAssigneeId() == null
					? "Assign this ticket to yourself before working on it"
					: "Another finance member is working on this ticket");
		}
	}

	@Transactional(readOnly = true)
	public List<Agent> agents() {
		return accounts.getAccounts(roles.usersWithPermission(PermissionCode.SUPPORT_TICKET_MANAGE.name())).values().stream()
			.map(a -> new Agent(a.id(), a.firstName() + " " + a.lastName(), a.email()))
			.sorted(Comparator.comparing(Agent::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	// ----------------------------------------------------------------------------- internal

	private void post(SupportTicket ticket, UUID authorId, boolean fromStaff, boolean internal, String body,
			List<MultipartFile> files, Instant now) {
		TicketMessage message = messages.saveAndFlush(new TicketMessage(ticket.getId(), authorId, fromStaff, internal,
				body.strip(), now));
		for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
			if (file == null || file.isEmpty()) {
				continue;
			}
			byte[] bytes = read(file);
			// The store validates size and the real type (from the bytes, not the name or declared type).
			UUID documentId = documents.store(authorId, DocumentPurpose.SUPPORT_ATTACHMENT, bytes);
			attachments.save(new TicketAttachment(ticket.getId(), message.getId(), documentId, filename(file),
					DocumentService.detectContentType(bytes), bytes.length, now));
		}
	}

	private Optional<String> relatedLabel(UUID userId, RelatedType type, UUID id) {
		return switch (type) {
			case ORDER -> orders.ownedOrderNumber(userId, id);
			case WITHDRAWAL -> withdrawals.ownedReference(userId, id);
			case HOLDING -> holdings.ownedLabel(userId, id);
		};
	}

	private TicketDetail detail(SupportTicket ticket, TicketResponse view, boolean staff) {
		List<TicketMessage> thread = messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
			.filter(m -> staff || !m.isInternal())
			.toList();
		Map<UUID, List<TicketAttachment>> files = attachments.findByTicketId(ticket.getId()).stream()
			.collect(Collectors.groupingBy(TicketAttachment::getMessageId));
		List<TicketEvent> history = events.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
			.filter(e -> staff || e.getType().visibleToCustomer())
			.toList();
		Set<UUID> people = new HashSet<>();
		thread.stream().map(TicketMessage::getAuthorId).filter(Objects::nonNull).forEach(people::add);
		history.forEach(e -> Stream.of(e.getActorId(), e.getSubjectUserId()).filter(Objects::nonNull).forEach(people::add));
		Map<UUID, UserAccount> authors = accounts.getAccounts(people);
		List<TicketDetail.Message> out = new ArrayList<>();
		for (TicketMessage m : thread) {
			UserAccount author = m.getAuthorId() == null ? null : authors.get(m.getAuthorId());
			// A message without an author was written by the system.
			String name = m.getAuthorId() == null ? "SeaLease support"
					: author == null ? null
					: staff ? author.firstName() + " " + author.lastName()
					: m.isFromStaff() ? author.firstName() + " (SeaLease support)" : "You";
			out.add(new TicketDetail.Message(m.getId(), m.isFromStaff(), m.isInternal(), name, m.getBody(),
					m.getCreatedAt(), files.getOrDefault(m.getId(), List.of()).stream()
						.map(a -> new TicketDetail.Attachment(a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes()))
						.toList()));
		}
		List<TicketDetail.Event> timeline = history.stream()
			.map(e -> new TicketDetail.Event(e.getId(), e.getType(), e.getFromValue(), e.getToValue(), e.isActorIsStaff(),
					personName(authors.get(e.getActorId()), e.isActorIsStaff(), staff),
					personName(authors.get(e.getSubjectUserId()), true, staff), e.getCreatedAt()))
			.toList();
		return new TicketDetail(view, out, timeline);
	}

	/** Staff see full names; investors see "You", and staff by first name only. */
	private static String personName(UserAccount person, boolean isStaff, boolean viewerIsStaff) {
		if (person == null) {
			return null;
		}
		return viewerIsStaff ? person.firstName() + " " + person.lastName()
				: isStaff ? person.firstName() + " (SeaLease support)" : "You";
	}

	private void event(SupportTicket ticket, UUID actorId, boolean byStaff, TicketEventType type, Enum<?> from, Enum<?> to,
			UUID subjectUserId, Instant now) {
		events.save(new TicketEvent(ticket.getId(), actorId, byStaff, type, from == null ? null : from.name(),
				to == null ? null : to.name(), subjectUserId, now));
	}

	/** For a resolved ticket, until when its customer can reopen it (when it closes by itself); otherwise null. */
	private Instant reopenableUntil(SupportTicket ticket) {
		return reopenableUntil(ticket, clock.instant());
	}

	private Instant reopenableUntil(SupportTicket ticket, Instant now) {
		if (ticket.getStatus() != TicketStatus.RESOLVED || ticket.getResolvedAt() == null) {
			return null;
		}
		Instant until = ticket.getResolvedAt().plus(properties.autoCloseAfter());
		return now.isAfter(until) ? null : until;
	}

	/**
	 * The customer may write on (and so reopen) a ticket that is not closed; a resolved one only until it closes by
	 * itself, even if the closing job has not run yet.
	 */
	private void requireReopenable(SupportTicket ticket) {
		if (ticket.getStatus() == TicketStatus.CLOSED
				|| (ticket.getStatus() == TicketStatus.RESOLVED && reopenableUntil(ticket) == null)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This request is closed and cannot be reopened; please open a new one");
		}
	}

	/**
	 * Closes resolved tickets whose customer has not reopened them within {@code app.support.auto-close-after}, each
	 * in its own transaction. Safe to run concurrently: each ticket is locked and re-checked.
	 *
	 * @return how many were closed
	 */
	public int closeResolved() {
		Instant cutoff = clock.instant().minus(properties.autoCloseAfter());
		int closed = 0;
		for (UUID id : tickets.findResolvedBefore(cutoff)) {
			if (Boolean.TRUE.equals(transactions.execute(status -> autoClose(id, cutoff)))) {
				closed++;
			}
		}
		return closed;
	}

	private boolean autoClose(UUID ticketId, Instant cutoff) {
		SupportTicket ticket = lock(ticketId);
		if (ticket.getStatus() != TicketStatus.RESOLVED || ticket.getResolvedAt().isAfter(cutoff)) {
			return false;
		}
		Instant now = clock.instant();
		ticket.close(now);
		tickets.flush();
		event(ticket, null, true, TicketEventType.STATUS_CHANGED, TicketStatus.RESOLVED, TicketStatus.CLOSED, null, now);
		audit.record(AuditRecord.of(null, AuditAction.TICKET_STATUS_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("status", TicketStatus.RESOLVED))
			.withNewValue(Map.of("status", TicketStatus.CLOSED, "automatic", true)));
		notifications.notify(ticket.getUserId(), "TICKET_STATUS", ticket.getReference() + " was closed", ticket.getSubject(),
				"/support/" + ticket.getId());
		automation.fire(AutomationTrigger.TICKET_CLOSED, ticketId, "closed");
		return true;
	}

	private TicketAttachment attachment(SupportTicket ticket, UUID attachmentId) {
		return attachments.findById(attachmentId).filter(a -> a.getTicketId().equals(ticket.getId()))
			.orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
	}

	private Map<UUID, String> names(Stream<UUID> ids) {
		Set<UUID> wanted = new HashSet<>();
		ids.filter(Objects::nonNull).forEach(wanted::add);
		return accounts.getAccounts(wanted).values().stream()
			.collect(Collectors.toMap(UserAccount::id, a -> a.firstName() + " " + a.lastName()));
	}

	private SupportTicket lockOwn(UUID userId, UUID ticketId) {
		return tickets.findByIdForUpdate(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
	}

	SupportTicket lock(UUID ticketId) {
		return tickets.findByIdForUpdate(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
	}

	private static void requireNotClosed(SupportTicket ticket) {
		if (ticket.getStatus() == TicketStatus.CLOSED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This request is closed; reopen it to continue");
		}
	}

	private static void requireFileCount(List<MultipartFile> files) {
		if (files != null && files.stream().filter(f -> f != null && !f.isEmpty()).count() > MAX_FILES) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Attach at most " + MAX_FILES + " files");
		}
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read an uploaded file", ex);
		}
	}

	/** The original name without any path, trimmed to fit; only for display and download names. */
	private static String filename(MultipartFile file) {
		String name = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
		if (name.isEmpty()) {
			name = "attachment";
		}
		return name.length() > 200 ? name.substring(name.length() - 200) : name;
	}

	private static Specification<SupportTicket> matching(UUID viewerId, TicketSearchCriteria c, Instant now, List<UUID> customers,
			DisplayTimeZone display) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.team() != null) {
				predicates.add(cb.equal(root.get("team"), c.team()));
			}
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.priority() != null) {
				predicates.add(cb.equal(root.get("priority"), c.priority()));
			}
			if (c.category() != null) {
				predicates.add(cb.equal(root.get("category"), c.category()));
			}
			if (c.subcategoryId() != null) {
				predicates.add(cb.equal(root.get("subcategoryId"), c.subcategoryId()));
			}
			if (c.channel() != null) {
				predicates.add(cb.equal(root.get("channel"), c.channel()));
			}
			if (c.customerId() != null) {
				predicates.add(cb.equal(root.get("userId"), c.customerId()));
			}
			if (c.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.from().atStartOfDay(display.zone()).toInstant()));
			}
			if (c.to() != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), c.to().plusDays(1).atStartOfDay(display.zone()).toInstant()));
			}
			if ("me".equals(c.assignee())) {
				predicates.add(cb.equal(root.get("assigneeId"), viewerId));
			}
			else if ("unassigned".equals(c.assignee())) {
				predicates.add(cb.isNull(root.get("assigneeId")));
			}
			else if (c.assignee() != null && !c.assignee().isBlank()) {
				try {
					predicates.add(cb.equal(root.get("assigneeId"), UUID.fromString(c.assignee())));
				}
				catch (IllegalArgumentException ex) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, "assignee must be me, unassigned or a user id");
				}
			}
			if (c.overdue()) {
				predicates.add(cb.isNull(root.get("firstRespondedAt")));
				predicates.add(cb.notEqual(root.get("status"), TicketStatus.CLOSED));
				predicates.add(cb.notEqual(root.get("status"), TicketStatus.RESOLVED));
				predicates.add(cb.lessThan(root.get("firstResponseDueAt"), now));
			}
			if (c.view() != null && !c.view().isBlank()) {
				Predicate open = root.get("status").in(TicketStatus.openStatuses());
				Predicate pastFirst = cb.and(cb.isNull(root.get("firstRespondedAt")), cb.lessThan(root.get("firstResponseDueAt"), now));
				Predicate pastNext = cb.lessThan(root.get("nextResponseDueAt"), now);
				Predicate pastResolution = cb.and(cb.isNull(root.get("slaPausedAt")), cb.lessThan(root.get("resolutionDueAt"), now));
				Predicate breached = cb.or(pastFirst, pastNext, pastResolution, cb.isNotNull(root.get("firstResponseBreachedAt")),
						cb.isNotNull(root.get("nextResponseBreachedAt")), cb.isNotNull(root.get("resolutionBreachedAt")));
				Instant soon = now.plus(Duration.ofHours(2));
				switch (c.view()) {
					case "open" -> predicates.add(open);
					case "breached" -> predicates.add(cb.and(open, breached));
					case "at-risk" -> predicates.add(cb.and(open, cb.not(breached), cb.or(
							cb.and(cb.isNull(root.get("firstRespondedAt")), cb.lessThan(root.get("firstResponseDueAt"), soon)),
							cb.lessThan(root.get("nextResponseDueAt"), soon),
							cb.and(cb.isNull(root.get("slaPausedAt")), cb.lessThan(root.get("resolutionDueAt"), soon)))));
					case "escalated" -> predicates.add(cb.and(open, cb.greaterThan(root.get("escalationLevel"), (short) 0)));
					case "waiting" -> predicates.add(root.get("status").in(TicketStatus.WAITING_ON_CUSTOMER, TicketStatus.WAITING_ON_INTERNAL));
					case "unassigned" -> predicates.add(cb.and(open, cb.isNull(root.get("assigneeId"))));
					default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"view must be open, breached, at-risk, escalated, waiting or unassigned");
				}
			}
			if (c.q() != null && !c.q().isBlank()) {
				String like = "%" + c.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				List<Predicate> any = new ArrayList<>(List.of(cb.like(cb.lower(root.get("reference")), like, '\\'),
						cb.like(cb.lower(root.get("subject")), like, '\\'),
						cb.like(cb.lower(root.get("relatedLabel")), like, '\\')));
				if (!customers.isEmpty()) {
					any.add(root.get("userId").in(customers));
				}
				predicates.add(cb.or(any.toArray(Predicate[]::new)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
