package com.sealease.backend.approval.service;

import com.sealease.backend.approval.dto.ApprovalDtos.ApprovalResponse;
import com.sealease.backend.approval.dto.ApprovalDtos.CreateApprovalRequest;
import com.sealease.backend.approval.entity.ApprovalRequest;
import com.sealease.backend.approval.entity.ApprovalStatus;
import com.sealease.backend.approval.entity.ApprovalType;
import com.sealease.backend.approval.repository.ApprovalRequestRepository;
import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.ledger.dto.AdjustmentRequest;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.payment.dto.RefundRequest;
import com.sealease.backend.payment.service.PaymentService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Maker-checker: one person asks, another decides, and the operation runs in the decision's transaction. A decision
 * locks its request first, so a request can only ever be decided, and executed, once. A failed operation rolls the
 * decision back and the request stays pending.
 */
@Service
public class ApprovalService {

	private static final String ENTITY = "APPROVAL_REQUEST";

	private final ApprovalRequestRepository requests;
	private final ApprovalProperties properties;
	private final LedgerService ledger;
	private final PaymentService payments;
	private final JsonMapper json;
	private final AuditService audit;
	private final Clock clock;

	public ApprovalService(ApprovalRequestRepository requests, ApprovalProperties properties, LedgerService ledger,
			PaymentService payments, JsonMapper json, AuditService audit, Clock clock) {
		this.requests = requests;
		this.properties = properties;
		this.ledger = ledger;
		this.payments = payments;
		this.json = json;
		this.audit = audit;
		this.clock = clock;
	}

	/** Refuses the direct route to an operation that must go through a request. */
	public void requireDirectAllowed(ApprovalType type) {
		if (properties.required().contains(type)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"This needs a second person: submit it for approval");
		}
	}

	@Transactional(readOnly = true)
	public Page<ApprovalResponse> list(ApprovalStatus status, Pageable pageable) {
		return (status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable))
			.map(ApprovalResponse::from);
	}

	@Transactional(readOnly = true)
	public ApprovalResponse get(UUID id) {
		return ApprovalResponse.from(load(id));
	}

	@Transactional
	public ApprovalResponse create(AuthenticatedUser maker, CreateApprovalRequest request) {
		requirePermission(maker, request.type());
		if (maker.userId().equals(request.assignedTo())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot assign a request to yourself");
		}
		String payload = payloadOf(request);
		ApprovalRequest saved = requests.saveAndFlush(new ApprovalRequest(request.type(), summaryOf(maker, request),
				request.reason().strip(), payload, maker.userId(), request.assignedTo()));
		audit.record(AuditRecord.of(maker.userId(), AuditAction.APPROVAL_REQUESTED, ENTITY, saved.getId())
			.withNewValue(Map.of("type", saved.getType(), "summary", saved.getSummary())));
		return ApprovalResponse.from(saved);
	}

	@Transactional
	public ApprovalResponse approve(AuthenticatedUser checker, UUID id) {
		ApprovalRequest request = lockForDecision(checker, id);
		String reference = execute(request, checker.userId());
		request.approve(checker.userId(), clock.instant(), reference);
		requests.flush();
		audit.record(AuditRecord.of(checker.userId(), AuditAction.APPROVAL_APPROVED, ENTITY, id)
			.withNewValue(Map.of("type", request.getType(), "reference", reference, "maker", request.getCreatedBy())));
		return ApprovalResponse.from(request);
	}

	@Transactional
	public ApprovalResponse reject(AuthenticatedUser checker, UUID id, String reason) {
		ApprovalRequest request = lockForDecision(checker, id);
		request.reject(checker.userId(), clock.instant(), reason.strip());
		requests.flush();
		audit.record(AuditRecord.of(checker.userId(), AuditAction.APPROVAL_REJECTED, ENTITY, id)
			.withNewValue(Map.of("type", request.getType(), "reason", reason.strip(), "maker", request.getCreatedBy())));
		return ApprovalResponse.from(request);
	}

	private ApprovalRequest lockForDecision(AuthenticatedUser checker, UUID id) {
		ApprovalRequest request = requests.findForDecision(id).orElseThrow(() -> new ResourceNotFoundException("Approval request", id));
		if (request.getStatus() != ApprovalStatus.PENDING) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This request has already been decided");
		}
		if (request.getCreatedBy().equals(checker.userId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide your own request");
		}
		if (request.getAssignedTo() != null && !request.getAssignedTo().equals(checker.userId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "This request is assigned to someone else");
		}
		requirePermission(checker, request.getType());
		return request;
	}

	/** Deciding must not give more power than doing: both people need the operation's own permission. */
	private static void requirePermission(AuthenticatedUser actor, ApprovalType type) {
		if (!actor.permissions().contains(type.permission().name())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "This needs the " + type.permission().name() + " permission");
		}
	}

	private String execute(ApprovalRequest request, UUID checkerId) {
		String key = request.getId().toString();
		switch (request.getType()) {
			case LEDGER_ADJUSTMENT -> ledger.adjust(checkerId, json.readValue(request.getPayload(), AdjustmentRequest.class), key);
			case PAYMENT_REFUND -> {
				RefundPayload refund = json.readValue(request.getPayload(), RefundPayload.class);
				payments.recordRefund(checkerId, refund.paymentId(), refund.refund());
			}
			default -> throw new IllegalStateException("No execution for " + request.getType());
		}
		return key;
	}

	private String payloadOf(CreateApprovalRequest request) {
		return switch (request.type()) {
			case LEDGER_ADJUSTMENT -> {
				if (request.adjustment() == null) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A ledger adjustment needs its details");
				}
				yield json.writeValueAsString(request.adjustment());
			}
			case PAYMENT_REFUND -> {
				if (request.paymentId() == null || request.refund() == null) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A refund needs its payment and details");
				}
				yield json.writeValueAsString(new RefundPayload(request.paymentId(), request.refund()));
			}
		};
	}

	/** Follows the type alone, so a checker reads exactly what approval will run, whatever else a request carries. */
	private static String summaryOf(AuthenticatedUser maker, CreateApprovalRequest request) {
		return switch (request.type()) {
			case LEDGER_ADJUSTMENT -> {
				AdjustmentRequest a = request.adjustment();
				if (a.userId().equals(maker.userId())) {
					throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot adjust your own balance");
				}
				yield a.direction() + " " + a.currency() + " " + a.amount().toPlainString() + " for user " + a.userId();
			}
			case PAYMENT_REFUND -> "Refund of payment " + request.paymentId() + ", reference " + request.refund().reference();
		};
	}

	private ApprovalRequest load(UUID id) {
		return requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Approval request", id));
	}

	private record RefundPayload(UUID paymentId, RefundRequest refund) {
	}

}
