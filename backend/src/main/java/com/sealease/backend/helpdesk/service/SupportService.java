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
import com.sealease.backend.helpdesk.dto.Agent;
import com.sealease.backend.helpdesk.dto.OpenTicketRequest;
import com.sealease.backend.helpdesk.dto.TicketDetail;
import com.sealease.backend.helpdesk.dto.TicketResponse;
import com.sealease.backend.helpdesk.dto.TicketSearchCriteria;
import com.sealease.backend.helpdesk.entity.RelatedType;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketAttachment;
import com.sealease.backend.helpdesk.entity.TicketMessage;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import com.sealease.backend.helpdesk.repository.SupportTicketRepository;
import com.sealease.backend.helpdesk.repository.TicketAttachmentRepository;
import com.sealease.backend.helpdesk.repository.TicketMessageRepository;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
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
 * Support tickets: investors open and reply; agents (SUPPORT_TICKET_MANAGE) reply, add internal
 * notes, prioritise, assign, resolve and close. Investors only ever see their own tickets, and never
 * internal notes or the attachments on them.
 */
@Service
public class SupportService {

	private static final String ENTITY = "SUPPORT_TICKET";
	private static final int MAX_FILES = 3;

	private final SupportTicketRepository tickets;
	private final TicketMessageRepository messages;
	private final TicketAttachmentRepository attachments;
	private final DocumentService documents;
	private final NotificationService notifications;
	private final OrderService orders;
	private final WithdrawalService withdrawals;
	private final HoldingService holdings;
	private final UserAccountService accounts;
	private final UserRoleService roles;
	private final AuditService audit;
	private final SupportProperties properties;
	private final Clock clock;

	public SupportService(SupportTicketRepository tickets, TicketMessageRepository messages,
			TicketAttachmentRepository attachments, DocumentService documents, NotificationService notifications,
			OrderService orders, WithdrawalService withdrawals, HoldingService holdings, UserAccountService accounts,
			UserRoleService roles, AuditService audit, SupportProperties properties, Clock clock) {
		this.tickets = tickets;
		this.messages = messages;
		this.attachments = attachments;
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
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_OPENED, ENTITY, ticket.getId())
			.withNewValue(Map.of("reference", ticket.getReference(), "category", ticket.getCategory())));
		return customerDetail(userId, ticket.getId());
	}

	/** The customer writes; a resolved ticket reopens. Closed tickets take no more messages. */
	@Transactional
	public TicketDetail customerReply(UUID userId, UUID ticketId, String body, List<MultipartFile> files) {
		requireFileCount(files);
		SupportTicket ticket = lockOwn(userId, ticketId);
		requireNotClosed(ticket);
		Instant now = clock.instant();
		post(ticket, userId, false, false, body, files, now);
		ticket.customerReplied(now);
		tickets.flush();
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
		ticket.close(clock.instant());
		tickets.flush();
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_STATUS_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", TicketStatus.CLOSED)));
		return customerDetail(userId, ticketId);
	}

	@Transactional(readOnly = true)
	public Page<TicketResponse> mine(UUID userId, Pageable pageable) {
		Instant now = clock.instant();
		return tickets.findByUserIdOrderByLastMessageAtDesc(userId, pageable).map(t -> TicketResponse.forCustomer(t, now));
	}

	@Transactional(readOnly = true)
	public TicketDetail customerDetail(UUID userId, UUID ticketId) {
		SupportTicket ticket = tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		return detail(ticket, TicketResponse.forCustomer(ticket, clock.instant()), false);
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

	/** A public reply puts the ticket in the customer's court and notifies them; an internal note changes nothing. */
	@Transactional
	public TicketDetail staffReply(UUID actorId, UUID ticketId, String body, boolean internal, List<MultipartFile> files) {
		requireFileCount(files);
		SupportTicket ticket = lock(ticketId);
		requireNotClosed(ticket);
		if (ticket.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot answer your own ticket");
		}
		Instant now = clock.instant();
		post(ticket, actorId, true, internal, body, files, now);
		if (!internal) {
			ticket.staffReplied(now);
			notifications.notify(ticket.getUserId(), "TICKET_REPLY", "Support replied to " + ticket.getReference(),
					ticket.getSubject(), "/support/" + ticket.getId());
		}
		tickets.flush();
		return staffDetail(ticketId);
	}

	@Transactional
	public TicketDetail setStatus(UUID actorId, UUID ticketId, TicketStatus target) {
		if (target != TicketStatus.RESOLVED && target != TicketStatus.CLOSED) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tickets are resolved or closed by staff; replies reopen them");
		}
		SupportTicket ticket = lock(ticketId);
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
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_STATUS_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("status", previous)).withNewValue(Map.of("status", target)));
		notifications.notify(ticket.getUserId(), "TICKET_STATUS", ticket.getReference() + " was "
				+ target.name().toLowerCase(Locale.ROOT), ticket.getSubject(), "/support/" + ticket.getId());
		return staffDetail(ticketId);
	}

	/** Changing priority moves the first-response target, unless staff have already responded. */
	@Transactional
	public TicketDetail setPriority(UUID actorId, UUID ticketId, TicketPriority priority) {
		SupportTicket ticket = lock(ticketId);
		TicketPriority previous = ticket.getPriority();
		if (previous == priority) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already " + priority);
		}
		ticket.reprioritise(priority, ticket.getFirstRespondedAt() != null ? ticket.getFirstResponseDueAt()
				: ticket.getCreatedAt().plus(properties.responseTargets().of(priority)));
		tickets.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_PRIORITY_CHANGED, ENTITY, ticketId)
			.withOldValue(Map.of("priority", previous)).withNewValue(Map.of("priority", priority)));
		return staffDetail(ticketId);
	}

	@Transactional
	public TicketDetail assign(UUID actorId, UUID ticketId, UUID assigneeId) {
		SupportTicket ticket = lock(ticketId);
		if (Objects.equals(ticket.getAssigneeId(), assigneeId)) {
			throw new BusinessException(ErrorCode.CONFLICT, "The ticket is already assigned that way");
		}
		if (assigneeId != null) {
			if (!roles.usersWithPermission(PermissionCode.SUPPORT_TICKET_MANAGE.name()).contains(assigneeId)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "That user cannot handle tickets");
			}
			if (assigneeId.equals(ticket.getUserId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A ticket cannot be assigned to its requester");
			}
		}
		ticket.assignTo(assigneeId);
		tickets.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ASSIGNED, ENTITY, ticketId)
			.withNewValue(assigneeId == null ? null : Map.of("assigneeId", assigneeId.toString())));
		if (assigneeId != null && !assigneeId.equals(actorId)) {
			notifications.notify(assigneeId, "TICKET_ASSIGNED", ticket.getReference() + " was assigned to you",
					ticket.getSubject(), "/admin/support/" + ticket.getId());
		}
		return staffDetail(ticketId);
	}

	@Transactional(readOnly = true)
	public Page<TicketResponse> search(UUID viewerId, TicketSearchCriteria criteria, Pageable pageable) {
		Instant now = clock.instant();
		Page<SupportTicket> page = tickets.findAll(matching(viewerId, criteria, now), pageable);
		Map<UUID, String> names = names(page.stream().flatMap(t -> Stream.of(t.getUserId(), t.getAssigneeId())));
		return page.map(t -> TicketResponse.forStaff(t, names.get(t.getUserId()), names.get(t.getAssigneeId()), now));
	}

	@Transactional(readOnly = true)
	public TicketDetail staffDetail(UUID ticketId) {
		SupportTicket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		Map<UUID, String> names = names(Stream.of(ticket.getUserId(), ticket.getAssigneeId()));
		return detail(ticket, TicketResponse.forStaff(ticket, names.get(ticket.getUserId()),
				names.get(ticket.getAssigneeId()), clock.instant()), true);
	}

	/** Staff downloads are audited: attachments can hold personal financial documents. */
	@Transactional
	public DocumentContent staffAttachment(UUID actorId, UUID ticketId, UUID attachmentId) {
		SupportTicket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		TicketAttachment attachment = attachment(ticket, attachmentId);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ATTACHMENT_VIEWED, ENTITY, ticketId)
			.withNewValue(Map.of("attachmentId", attachmentId.toString(), "filename", attachment.getFilename())));
		return documents.load(attachment.getDocumentId());
	}

	/** Tickets waiting for staff, and those past their first-response target. */
	@Transactional(readOnly = true)
	public QueueCounts queueCounts() {
		return new QueueCounts(tickets.countByStatus(TicketStatus.OPEN), tickets.countOverdue(clock.instant()));
	}

	public record QueueCounts(long open, long overdue) {
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
		Map<UUID, UserAccount> authors = accounts.getAccounts(thread.stream().map(TicketMessage::getAuthorId).collect(Collectors.toSet()));
		List<TicketDetail.Message> out = new ArrayList<>();
		for (TicketMessage m : thread) {
			UserAccount author = authors.get(m.getAuthorId());
			String name = author == null ? null
					: staff ? author.firstName() + " " + author.lastName()
					: m.isFromStaff() ? author.firstName() + " (SeaLease support)" : "You";
			out.add(new TicketDetail.Message(m.getId(), m.isFromStaff(), m.isInternal(), name, m.getBody(),
					m.getCreatedAt(), files.getOrDefault(m.getId(), List.of()).stream()
						.map(a -> new TicketDetail.Attachment(a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes()))
						.toList()));
		}
		return new TicketDetail(view, out);
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
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This ticket is closed; open a new one");
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
