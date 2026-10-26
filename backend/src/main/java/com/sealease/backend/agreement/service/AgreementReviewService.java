package com.sealease.backend.agreement.service;

import com.sealease.backend.agreement.dto.AgreementPdf;
import com.sealease.backend.agreement.dto.AgreementReviewDetail;
import com.sealease.backend.agreement.dto.AgreementReviewTaskResponse;
import com.sealease.backend.agreement.dto.LeaseAgreementDocument;
import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.entity.AgreementReviewComment;
import com.sealease.backend.agreement.entity.AgreementReviewTask;
import com.sealease.backend.agreement.entity.LeaseAgreement;
import com.sealease.backend.agreement.repository.AgreementReviewCommentRepository;
import com.sealease.backend.agreement.repository.AgreementReviewTaskRepository;
import com.sealease.backend.agreement.repository.LeaseAgreementRepository;
import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Review tasks for lease agreements: every agreement issued opens one ({@link LeaseAgreementService#issue}). A
 * reviewer assigns a task to themselves, reads the agreement, and marks it done; only the assignee may complete it,
 * and they can give it back to the queue unfinished. Any reviewer may comment on any task.
 */
@Service
public class AgreementReviewService {

	private static final String ENTITY = "AGREEMENT_REVIEW_TASK";

	private final AgreementReviewTaskRepository tasks;
	private final AgreementReviewCommentRepository comments;
	private final LeaseAgreementRepository agreements;
	private final LeaseAgreementService leaseAgreements;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final Clock clock;

	public AgreementReviewService(AgreementReviewTaskRepository tasks, AgreementReviewCommentRepository comments,
			LeaseAgreementRepository agreements, LeaseAgreementService leaseAgreements, UserAccountService accounts,
			AuditService audit, Clock clock) {
		this.tasks = tasks;
		this.comments = comments;
		this.agreements = agreements;
		this.leaseAgreements = leaseAgreements;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Open tasks oldest first, done ones most recently completed first; with {@code mineOnly}, only those assigned to
	 * {@code viewerId}.
	 */
	@Transactional(readOnly = true)
	public Page<AgreementReviewTaskResponse> search(UUID viewerId, AgreementReviewTask.Status status, boolean mineOnly,
			Pageable pageable) {
		Sort order = status == AgreementReviewTask.Status.OPEN ? Sort.by("createdAt", "id")
				: Sort.by(Sort.Order.desc("completedAt"), Sort.Order.desc("id"));
		Page<AgreementReviewTask> page = tasks.search(status, mineOnly ? viewerId : null,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order));
		return new PageImpl<>(responses(page.getContent()), page.getPageable(), page.getTotalElements());
	}

	@Transactional(readOnly = true)
	public long openCount() {
		return tasks.countByStatus(AgreementReviewTask.Status.OPEN);
	}

	@Transactional(readOnly = true)
	public AgreementReviewDetail detail(UUID taskId) {
		AgreementReviewTask task = tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Agreement review task", taskId));
		return detail(task);
	}

	/** The agreement as a PDF, as the investor would download it. */
	@Transactional(readOnly = true)
	public AgreementPdf pdf(UUID taskId) {
		AgreementReviewTask task = tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Agreement review task", taskId));
		return leaseAgreements.pdf(agreement(task).getHoldingId(), null);
	}

	/** Takes an open task: from now on only this reviewer may complete it. Taking one already yours changes nothing. */
	@Transactional
	public AgreementReviewDetail assign(UUID reviewerId, UUID taskId) {
		AgreementReviewTask task = open(taskId);
		if (task.isAssignedTo(reviewerId)) {
			return detail(task);
		}
		if (task.getAssignedTo() != null) {
			throw new BusinessException(ErrorCode.CONFLICT,
					"This task is already assigned to " + name(accounts.getAccount(task.getAssignedTo())));
		}
		task.assignTo(reviewerId, clock.instant());
		tasks.flush();
		record(reviewerId, AuditAction.AGREEMENT_REVIEW_ASSIGNED, task);
		return detail(task);
	}

	/** The assignee gives an open task back to the queue, unfinished, for another reviewer to take. */
	@Transactional
	public AgreementReviewDetail unassign(UUID reviewerId, UUID taskId) {
		AgreementReviewTask task = assignedTo(reviewerId, taskId);
		task.unassign();
		tasks.flush();
		record(reviewerId, AuditAction.AGREEMENT_REVIEW_UNASSIGNED, task);
		return detail(task);
	}

	@Transactional
	public AgreementReviewDetail comment(UUID authorId, UUID taskId, String body) {
		AgreementReviewTask task = tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Agreement review task", taskId));
		comments.saveAndFlush(new AgreementReviewComment(task.getId(), authorId, body.strip(), clock.instant()));
		return detail(task);
	}

	/** The assignee marks the task done, with an optional closing comment. */
	@Transactional
	public AgreementReviewDetail complete(UUID reviewerId, UUID taskId, String comment) {
		AgreementReviewTask task = assignedTo(reviewerId, taskId);
		if (comment != null && !comment.isBlank()) {
			comments.save(new AgreementReviewComment(task.getId(), reviewerId, comment.strip(), clock.instant()));
		}
		task.complete(reviewerId, clock.instant());
		tasks.flush();
		record(reviewerId, AuditAction.AGREEMENT_REVIEW_COMPLETED, task);
		return detail(task);
	}

	private AgreementReviewTask open(UUID taskId) {
		AgreementReviewTask task = tasks.findByIdForUpdate(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Agreement review task", taskId));
		if (task.getStatus() != AgreementReviewTask.Status.OPEN) {
			throw new BusinessException(ErrorCode.CONFLICT, "This task is already done");
		}
		return task;
	}

	/** An open task assigned to this reviewer: only they may complete it or give it back. */
	private AgreementReviewTask assignedTo(UUID reviewerId, UUID taskId) {
		AgreementReviewTask task = open(taskId);
		if (!task.isAssignedTo(reviewerId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, task.getAssignedTo() == null
					? "Assign this task to yourself first"
					: "This task is assigned to " + name(accounts.getAccount(task.getAssignedTo())));
		}
		return task;
	}

	private AgreementReviewDetail detail(AgreementReviewTask task) {
		LeaseAgreement agreement = agreement(task);
		List<AgreementReviewComment> found = comments.findByTaskIdOrderByCreatedAt(task.getId());
		Map<UUID, UserAccount> authors = accounts.getAccounts(
				found.stream().map(AgreementReviewComment::getAuthorId).collect(Collectors.toSet()));
		LeaseAgreementDocument document = leaseAgreements.document(agreement.getHoldingId(), null);
		return new AgreementReviewDetail(responses(List.of(task)).getFirst(), document.body(),
				found.stream().map(c -> new AgreementReviewDetail.Comment(c.getId(), c.getAuthorId(),
						name(authors.get(c.getAuthorId())), c.getBody(), c.getCreatedAt())).toList());
	}

	/** Responses for a page of tasks, with their agreements, investors, reviewers and comment counts in a few queries. */
	private List<AgreementReviewTaskResponse> responses(List<AgreementReviewTask> page) {
		if (page.isEmpty()) {
			return List.of();
		}
		Map<UUID, LeaseAgreementResponse> byAgreement = leaseAgreements.byIds(
				page.stream().map(AgreementReviewTask::getAgreementId).toList()).stream()
			.collect(Collectors.toMap(LeaseAgreementResponse::id, Function.identity()));
		Map<UUID, UUID> investorOf = agreements.findAllById(page.stream().map(AgreementReviewTask::getAgreementId).toList())
			.stream().collect(Collectors.toMap(LeaseAgreement::getId, LeaseAgreement::getUserId));
		Set<UUID> people = new HashSet<>(investorOf.values());
		page.forEach(t -> {
			if (t.getAssignedTo() != null) {
				people.add(t.getAssignedTo());
			}
			if (t.getCompletedBy() != null) {
				people.add(t.getCompletedBy());
			}
		});
		Map<UUID, UserAccount> users = accounts.getAccounts(people);
		Map<UUID, Long> commentCounts = new HashMap<>();
		comments.countByTask(page.stream().map(AgreementReviewTask::getId).toList())
			.forEach(row -> commentCounts.put((UUID) row[0], (Long) row[1]));
		return page.stream().map(t -> {
			UserAccount investor = users.get(investorOf.get(t.getAgreementId()));
			return new AgreementReviewTaskResponse(t.getId(), t.getStatus(), byAgreement.get(t.getAgreementId()),
					new AgreementReviewTaskResponse.Investor(investor.id(), investor.userCode(), name(investor),
							investor.email()),
					t.getAssignedTo() == null ? null
							: new Assignee(t.getAssignedTo(), name(users.get(t.getAssignedTo())), t.getAssignedAt()),
					t.getCompletedBy() == null ? null
							: new Assignee(t.getCompletedBy(), name(users.get(t.getCompletedBy())), t.getCompletedAt()),
					commentCounts.getOrDefault(t.getId(), 0L), t.getCreatedAt());
		}).toList();
	}

	private LeaseAgreement agreement(AgreementReviewTask task) {
		return agreements.findById(task.getAgreementId())
			.orElseThrow(() -> new IllegalStateException("Agreement of task " + task.getId() + " is missing"));
	}

	private void record(UUID actorId, AuditAction action, AgreementReviewTask task) {
		audit.record(AuditRecord.of(actorId, action, ENTITY, task.getId())
			.withNewValue(Map.of("agreementId", task.getAgreementId().toString())));
	}

	private static String name(UserAccount user) {
		return user == null ? "Unknown" : (user.firstName() + " " + user.lastName()).strip();
	}

}
