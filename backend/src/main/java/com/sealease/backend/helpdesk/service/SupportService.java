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
import com.sealease.backend.reporting.dto.Workload;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.helpdesk.dto.Agent;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
	private final Clock clock;

	public SupportService(SupportTicketRepository tickets, TicketMessageRepository messages,
			TicketAttachmentRepository attachments, TicketEventRepository events, DocumentService documents, NotificationService notifications,
			OrderService orders, WithdrawalService withdrawals, HoldingService holdings, UserAccountService accounts,
			UserRoleService roles, AuditService audit, SupportProperties properties, TransactionTemplate transactions,
			Clock clock) {
		this.transactions = transactions;
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
		TicketPriority priority = TicketPriority.NORMAL;
		SupportTicket ticket = tickets.saveAndFlush(new SupportTicket("TK-" + String.format("%06d", tickets.nextNumber()),
				userId, request.subject().strip(), request.category(), request.relatedType(), request.relatedId(),
				relatedLabel, priority, now.plus(properties.responseTargets().of(priority)), now));
		post(ticket, userId, false, false, request.message(), files, now);
		event(ticket, userId, false, TicketEventType.OPENED, null, TicketStatus.OPEN, null, now);
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_OPENED, ENTITY, ticket.getId())
			.withNewValue(Map.of("reference", ticket.getReference(), "category", ticket.getCategory())));
		return customerDetail(userId, ticket.getId());
	}

	/** The customer writes; a resolved ticket reopens. A closed ticket must be reopened first. */
	@Transactional
	public TicketDetail customerReply(UUID userId, UUID ticketId, String body, List<MultipartFile> files) {
		requireFileCount(files);
		SupportTicket ticket = lockOwn(userId, ticketId);
		requireReopenable(ticket);
		Instant now = clock.instant();
		TicketStatus previous = ticket.getStatus();
		post(ticket, userId, false, false, body, files, now);
		ticket.customerReplied(now);
		tickets.flush();
		if (previous == TicketStatus.RESOLVED) {
			event(ticket, userId, false, TicketEventType.REOPENED, previous, TicketStatus.OPEN, null, now);
		}
		else if (previous != TicketStatus.OPEN) {
			event(ticket, userId, false, TicketEventType.STATUS_CHANGED, previous, TicketStatus.OPEN, null, now);
		}
		if (ticket.getAssigneeId() != null) {
			notifications.notify(ticket.getAssigneeId(), "TICKET_CUSTOMER_REPLY", "New reply on " + ticket.getReference(),
					ticket.getSubject(), "/admin/support/" + ticket.getId());
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
		ticket.reopen(now);
		tickets.flush();
		event(ticket, userId, false, TicketEventType.REOPENED, previous, TicketStatus.OPEN, null, now);
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_REOPENED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", TicketStatus.OPEN)));
		if (ticket.getAssigneeId() != null) {
			notifications.notify(ticket.getAssigneeId(), "TICKET_REOPENED", ticket.getReference() + " was reopened",
					ticket.getSubject(), "/admin/support/" + ticket.getId());
		}
		return customerDetail(userId, ticketId);
	}

	@Transactional(readOnly = true)
	public Page<TicketResponse> mine(UUID userId, Pageable pageable) {
		Instant now = clock.instant();
		return tickets.findByUserIdOrderByLastMessageAtDesc(userId, pageable).map(t -> TicketResponse.forCustomer(t, now, reopenableUntil(t)));
	}

	@Transactional(readOnly = true)
	public TicketDetail customerDetail(UUID userId, UUID ticketId) {
		SupportTicket ticket = tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		return detail(ticket, TicketResponse.forCustomer(ticket, clock.instant(), reopenableUntil(ticket)), false);
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
		SupportTicket ticket = tickets.saveAndFlush(new SupportTicket("TK-" + String.format("%06d", tickets.nextNumber()),
				customerId, subject, category, relatedType, relatedId, relatedLabel, priority,
				now.plus(properties.responseTargets().of(priority)), now));
		post(ticket, actorId, true, false, message, List.of(), now);
		event(ticket, actorId, true, TicketEventType.OPENED, null, TicketStatus.OPEN, null, now);
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
			event(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previous, TicketStatus.RESOLVED, null, now);
		}
		tickets.flush();
		notifications.notify(ticket.getUserId(), "TICKET_STATUS", ticket.getReference() + " was resolved", note,
				"/support/" + ticket.getId());
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
			if (previous != TicketStatus.WAITING_ON_CUSTOMER) {
				event(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previous, TicketStatus.WAITING_ON_CUSTOMER, null, now);
			}
			notifications.notify(ticket.getUserId(), "TICKET_REPLY", "Support replied to " + ticket.getReference(),
					ticket.getSubject(), "/support/" + ticket.getId());
		}
		tickets.flush();
		return staffDetail(ticketId);
	}

	@Transactional
	public TicketDetail setStatus(UUID actorId, boolean financeOnly, UUID ticketId, TicketStatus target) {
		if (target != TicketStatus.RESOLVED && target != TicketStatus.CLOSED) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tickets are resolved or closed by our support team; replies reopen them");
		}
		SupportTicket ticket = lock(ticketId);
		requireWorkingOn(ticket, actorId, financeOnly);
		requireNotClosed(ticket);
		TicketStatus previous = ticket.getStatus();
		if (previous == target) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already " + target);
		}
		Instant now = clock.instant();
		if (target == TicketStatus.RESOLVED) {
			ticket.resolve(now);
		}
		else {
			ticket.close(now);
		}
		tickets.flush();
		event(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previous, target, null, now);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_STATUS_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", target)));
		notifications.notify(ticket.getUserId(), "TICKET_STATUS", ticket.getReference() + " was "
				+ target.name().toLowerCase(Locale.ROOT), ticket.getSubject(), "/support/" + ticket.getId());
		return staffDetail(ticketId);
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
		ticket.reprioritise(priority, ticket.getFirstRespondedAt() != null ? ticket.getFirstResponseDueAt()
				: ticket.getCreatedAt().plus(properties.responseTargets().of(priority)));
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
		ticket.assignTo(assigneeId);
		tickets.flush();
		event(ticket, actorId, true, assigneeId == null ? TicketEventType.UNASSIGNED : TicketEventType.ASSIGNED, null, null,
				assigneeId, clock.instant());
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ASSIGNED, ENTITY, ticketId)
			.withNewValue(assigneeId == null ? null : Map.of("assigneeId", assigneeId.toString())));
		if (assigneeId != null && !assigneeId.equals(actorId)) {
			notifications.notify(assigneeId, "TICKET_ASSIGNED", ticket.getReference() + " was assigned to you",
					ticket.getSubject(), "/admin/support/" + ticket.getId());
		}
		return staffDetail(ticketId);
	}

	@Transactional(readOnly = true)
	public Page<TicketResponse> search(UUID viewerId, boolean financeOnly, TicketSearchCriteria criteria, Pageable pageable) {
		Instant now = clock.instant();
		if (financeOnly) {
			criteria = new TicketSearchCriteria(criteria.q(), criteria.status(), criteria.priority(), criteria.assignee(),
					criteria.overdue(), SupportTeam.FINANCE);
		}
		Page<SupportTicket> page = tickets.findAll(matching(viewerId, criteria, now), pageable);
		Map<UUID, String> names = names(page.stream().flatMap(t -> Stream.of(t.getUserId(), t.getAssigneeId())));
		return page.map(t -> TicketResponse.forStaff(t, names.get(t.getUserId()), names.get(t.getAssigneeId()), now,
				reopenableUntil(t)));
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
		return detail(ticket, TicketResponse.forStaff(ticket, names.get(ticket.getUserId()),
				names.get(ticket.getAssigneeId()), clock.instant(), reopenableUntil(ticket)), true);
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
						ticket.getReference() + " was handed to finance", ticket.getSubject(), "/admin/support/" + ticket.getId()));
		}
		return staffDetail(ticketId);
	}

	/** Who may be assigned a ticket with this team: support agents, and finance members for tickets handed to finance. */
	private Set<UUID> handlers(SupportTeam team) {
		Set<UUID> ids = new HashSet<>(roles.usersWithPermission(PermissionCode.SUPPORT_TICKET_MANAGE.name()));
		if (team == SupportTeam.FINANCE) {
			ids.addAll(roles.usersWithPermission(PermissionCode.FINANCE_TICKET_HANDLE.name()));
		}
		return ids;
	}

	/** Finance only sees the tickets handed to it; anything else is not found, as if it did not exist. */
	private static void requireVisible(SupportTicket ticket, boolean financeOnly) {
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

	/** A team's tickets pending, the viewer's among them, and done in each period ({@code [from, to)}) by anyone and by the viewer. */
	@Transactional(readOnly = true)
	public Workload.Counts workload(SupportTeam team, UUID viewerId, Instant today, Instant month, Instant to) {
		return new Workload.Counts(tickets.countPendingForTeam(team, null), tickets.countOverdueForTeam(team, clock.instant()),
				tickets.countPendingForTeam(team, viewerId),
				tickets.countDoneForTeam(team, null, today, to), tickets.countDoneForTeam(team, viewerId, today, to),
				tickets.countDoneForTeam(team, null, month, to), tickets.countDoneForTeam(team, viewerId, month, to));
	}

	/** A team's pending tickets for each priority, most urgent first; every priority has a row. */
	@Transactional(readOnly = true)
	public List<Workload.PriorityRow> pendingByPriority(SupportTeam team, UUID viewerId) {
		Map<TicketPriority, Object[]> rows = new EnumMap<>(TicketPriority.class);
		tickets.pendingByPriority(team, viewerId, clock.instant()).forEach(row -> rows.put((TicketPriority) row[0], row));
		List<Workload.PriorityRow> out = new ArrayList<>();
		for (int i = TicketPriority.values().length - 1; i >= 0; i--) {
			TicketPriority priority = TicketPriority.values()[i];
			Object[] row = rows.get(priority);
			out.add(row == null ? new Workload.PriorityRow(priority, 0, 0, 0, 0)
					: new Workload.PriorityRow(priority, count(row[1]), count(row[2]), count(row[3]), count(row[4])));
		}
		return out;
	}

	private static long count(Object value) {
		return value == null ? 0 : ((Number) value).longValue();
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

	/** Until when the customer can reopen a resolved or closed ticket; null when it is in progress or past the window. */
	/** For a resolved ticket, until when its customer can reopen it (when it closes by itself); otherwise null. */
	private Instant reopenableUntil(SupportTicket ticket) {
		if (ticket.getStatus() != TicketStatus.RESOLVED || ticket.getResolvedAt() == null) {
			return null;
		}
		Instant until = ticket.getResolvedAt().plus(properties.autoCloseAfter());
		return clock.instant().isAfter(until) ? null : until;
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

	private SupportTicket lock(UUID ticketId) {
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

	private static Specification<SupportTicket> matching(UUID viewerId, TicketSearchCriteria c, Instant now) {
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
				predicates.add(cb.lessThan(root.get("firstResponseDueAt"), now));
			}
			if (c.q() != null && !c.q().isBlank()) {
				String like = "%" + c.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("reference")), like, '\\'),
						cb.like(cb.lower(root.get("subject")), like, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
