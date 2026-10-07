package com.sealease.backend.payment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.order.dto.PayableOrder;
import com.sealease.backend.order.entity.OrderStatus;
import com.sealease.backend.order.event.OrderClosedEvent;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.payment.dto.BankTransferInstructions;
import com.sealease.backend.payment.dto.CompanyBankAccountResponse;
import com.sealease.backend.payment.dto.ConfirmTransferRequest;
import com.sealease.backend.payment.dto.DepositDetails;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.PaymentSearchCriteria;
import com.sealease.backend.payment.dto.RefundRequest;
import com.sealease.backend.payment.dto.SubmitDepositRequest;
import com.sealease.backend.payment.entity.CompanyBankAccount;
import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;
import com.sealease.backend.payment.provider.BankTransferProperties;
import com.sealease.backend.payment.provider.PaymentProvider;
import com.sealease.backend.payment.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Collecting money for orders.
 *
 * <p>Lock order is always <em>order, then payment</em> (the same as checkout and expiry), so
 * settlement, a new payment attempt and the expiry sweep serialise on the order without deadlocks.
 * A successful payment confirms its order in the same transaction. Money that arrives when the
 * order can no longer be confirmed is never silently kept: the payment becomes REFUND_REQUIRED.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
	private static final String ENTITY = "PAYMENT";

	private final PaymentRepository payments;
	private final OrderService orders;
	private final Map<PaymentMethod, PaymentProvider> providersByMethod = new EnumMap<>(PaymentMethod.class);
	private final CompanyBankAccountService companyAccounts;
	private final BankTransferProperties bankTransfer;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final Clock clock;

	public PaymentService(PaymentRepository payments, OrderService orders, List<PaymentProvider> providers,
			CompanyBankAccountService companyAccounts, BankTransferProperties bankTransfer, OutboxPublisher outbox,
			AuditService audit, Clock clock) {
		this.payments = payments;
		this.orders = orders;
		for (PaymentProvider provider : providers) {
			if (providersByMethod.putIfAbsent(provider.method(), provider) != null) {
				throw new IllegalStateException("More than one payment provider for " + provider.method());
			}
		}
		this.companyAccounts = companyAccounts;
		this.bankTransfer = bankTransfer;
		this.outbox = outbox;
		this.audit = audit;
		this.clock = clock;
	}

	// --------------------------------------------------------------------------- investor

	/**
	 * Starts paying an order by bank (the only method offered). Idempotent per key; asking again while
	 * a bank payment is in flight resumes it, and an earlier card attempt still in flight is cancelled.
	 */
	@Transactional
	public PaymentResponse start(UUID userId, UUID orderId, PaymentMethod method, String idempotencyKey) {
		String key = IdempotencyKey.require(idempotencyKey);
		PayableOrder order = orders.lockForPayment(orderId);
		if (!order.userId().equals(userId)) {
			throw new ResourceNotFoundException("Order", orderId);
		}
		Optional<Payment> replay = payments.findByOrderIdAndIdempotencyKey(orderId, key);
		if (replay.isPresent()) {
			if (replay.get().getMethod() != method) {
				throw new BusinessException(ErrorCode.CONFLICT,
						IdempotencyKey.HEADER + " was already used for a different payment");
			}
			return view(replay.get(), order.orderNumber());
		}
		if (!order.acceptsPaymentAt(clock.instant())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, order.status() == OrderStatus.PENDING_PAYMENT
					? "The payment window for this order has passed" : "This order is no longer awaiting payment");
		}
		PaymentProvider provider = providersByMethod.get(method);
		if (method != PaymentMethod.BANK_TRANSFER) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Card payments are not accepted; please pay by bank");
		}
		if (provider == null || !companyAccounts.anyActive()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					humanize(method) + " payments are not available");
		}
		for (Payment inFlight : payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
			if (inFlight.getMethod() == method) {
				return view(inFlight, order.orderNumber());
			}
			inFlight.cancel("Replaced by a " + humanize(method).toLowerCase(Locale.ROOT) + " payment");
		}
		payments.flush();

		Payment payment = payments.saveAndFlush(new Payment(orderId, userId, method, provider.name(),
				provider.open(order.orderNumber(), order.total()), order.total(), key));
		audit.record(AuditRecord.of(userId, AuditAction.PAYMENT_STARTED, ENTITY, payment.getId())
			.withNewValue(Map.of("orderId", orderId.toString(), "method", method, "amount", order.total().toString())));
		return view(payment, order.orderNumber());
	}

	/**
	 * The investor tells us how they paid a bank payment: into which company account, and the
	 * transaction id, cheque number or deposit receipt number. The order then waits for finance to
	 * verify the money instead of lapsing. Details may be corrected until the payment is settled.
	 */
	@Transactional
	public PaymentResponse submitDeposit(UUID userId, UUID paymentId, SubmitDepositRequest request) {
		Payment payment = load(paymentId);
		if (!payment.getUserId().equals(userId)) {
			throw new ResourceNotFoundException("Payment", paymentId);
		}
		if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only bank payments take payment details");
		}
		PayableOrder order = orders.lockForPayment(payment.getOrderId());
		Payment locked = lockPayment(paymentId);
		if (locked.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + humanize(locked.getStatus()));
		}
		Instant now = clock.instant();
		if (!order.acceptsPaymentAt(now)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This order is no longer awaiting payment");
		}
		CompanyBankAccount account = companyAccounts.requireActive(request.companyBankAccountId());
		String reference = request.reference().strip().toUpperCase(Locale.ROOT);
		locked.submitDeposit(account.getId(), request.mode(), reference, now);
		payments.flush();
		order = orders.holdForVerification(order.id(), now.plus(bankTransfer.verificationWindow()));
		audit.record(AuditRecord.of(userId, AuditAction.PAYMENT_DETAILS_SUBMITTED, ENTITY, paymentId)
			.withNewValue(Map.of("companyBankAccountId", account.getId().toString(), "mode", request.mode(),
					"reference", reference, "orderExpiresAt", order.expiresAt().toString())));
		return view(locked, order.orderNumber());
	}

	@Transactional(readOnly = true)
	public List<PaymentResponse> forOrder(UUID orderId, UUID ownerId) {
		PayableOrder order = orders.payable(orderId);
		if (ownerId != null && !order.userId().equals(ownerId)) {
			throw new ResourceNotFoundException("Order", orderId);
		}
		List<Payment> found = payments.findByOrderIdOrderByCreatedAtDesc(orderId);
		Map<UUID, CompanyBankAccount> accounts = companyAccounts.byId(depositAccountIds(found));
		List<CompanyBankAccountResponse> active = companyAccounts.active();
		return found.stream().map(p -> view(p, order.orderNumber(), accounts, active)).toList();
	}

	// -------------------------------------------------------------------------------- staff

	/** Finance records that a bank transfer arrived. Nobody may confirm a payment for their own order. */
	@Transactional
	public PaymentResponse confirmBankTransfer(UUID actorId, UUID paymentId, ConfirmTransferRequest request) {
		Payment payment = load(paymentId);
		if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only bank transfers are confirmed manually");
		}
		if (payment.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot confirm a payment for your own order");
		}
		PayableOrder order = orders.lockForPayment(payment.getOrderId());
		Payment locked = lockPayment(paymentId);
		if (locked.getStatus() != PaymentStatus.PENDING && locked.getStatus() != PaymentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + locked.getStatus());
		}
		Money received = Money.of(request.amountReceived(), locked.amount().currency());
		if (!received.equals(locked.amount())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The amount due is "
					+ locked.amount().display() + "; partial or excess transfers must be returned to the investor");
		}
		settle(locked, order, received, request.externalReference().strip(), actorId);
		return view(locked, order.orderNumber());
	}

	/**
	 * Finance could not find the money (or the cheque bounced). The order keeps its reservation until
	 * it expires, so the investor can pay again or correct the details in a new payment.
	 */
	@Transactional
	public PaymentResponse rejectBankPayment(UUID actorId, UUID paymentId, String reason) {
		Payment payment = load(paymentId);
		if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only bank payments are rejected manually");
		}
		if (payment.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide on a payment for your own order");
		}
		PayableOrder order = orders.lockForPayment(payment.getOrderId());
		Payment locked = lockPayment(paymentId);
		if (locked.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + humanize(locked.getStatus()));
		}
		String note = reason.strip();
		locked.fail(note);
		payments.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_REJECTED, ENTITY, paymentId)
			.withNewValue(Map.of("reason", note)));
		outbox.publish(DomainEvent.of(KafkaTopics.PAYMENT_FAILED, "PaymentFailed", ENTITY, paymentId,
				Map.of("paymentId", paymentId.toString(), "orderId", order.id().toString(),
						"userId", locked.getUserId().toString(), "reason", note)));
		return view(locked, order.orderNumber());
	}

	/** Finance records that money held for an order that could not be confirmed was paid back. */
	@Transactional
	public PaymentResponse recordRefund(UUID actorId, UUID paymentId, RefundRequest request) {
		Payment payment = lockPayment(paymentId);
		if (payment.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot refund a payment of your own");
		}
		if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only payments awaiting a refund can be refunded");
		}
		payment.markRefunded(actorId, request.reference().strip(), request.reason().strip(), clock.instant());
		payments.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_REFUNDED, ENTITY, paymentId)
			.withNewValue(Map.of("amount", payment.amount().toString(), "reference", request.reference().strip(),
					"reason", request.reason().strip())));
		return view(payment, orders.payable(payment.getOrderId()).orderNumber());
	}

	@Transactional(readOnly = true)
	public Page<PaymentResponse> search(PaymentSearchCriteria criteria, Pageable pageable) {
		Page<Payment> page = payments.findAll(matching(criteria), pageable);
		Map<UUID, String> numbers = orders.orderNumbers(page.map(Payment::getOrderId).toList());
		Map<UUID, CompanyBankAccount> accounts = companyAccounts.byId(depositAccountIds(page.getContent()));
		List<CompanyBankAccountResponse> active = companyAccounts.active();
		return page.map(p -> view(p, numbers.get(p.getOrderId()), accounts, active));
	}

	// ----------------------------------------------------------------------------- internal

	/** A closed order takes no more money: cancel attempts still in flight (same transaction). */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onOrderClosed(OrderClosedEvent event) {
		for (Payment payment : payments.findByOrderIdAndStatus(event.orderId(), PaymentStatus.PENDING)) {
			payment.cancel("Order " + event.status().name().toLowerCase(Locale.ROOT));
		}
		payments.flush();
	}

	/** Records that money arrived; confirms the order when it can, otherwise flags a refund. */
	private void settle(Payment payment, PayableOrder order, Money received, String externalReference, UUID actorId) {
		if (payment.getStatus().isSettled()) {
			return;
		}
		String note = null;
		if (received == null || !received.equals(payment.amount())) {
			note = "Amount received (" + (received == null ? "unknown" : received.display()) + ") differs from "
					+ payment.amount().display();
		}
		else if (order.status() != OrderStatus.PENDING_PAYMENT) {
			note = "Order was " + order.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')
					+ " when the money arrived";
		}
		boolean accepted = note == null;
		if (accepted) {
			for (Payment other : payments.findByOrderIdAndStatus(order.id(), PaymentStatus.PENDING)) {
				if (!other.getId().equals(payment.getId())) {
					other.cancel("Order paid by another payment");
				}
			}
		}
		Instant now = clock.instant();
		payment.settle(accepted, externalReference, actorId, note, now);
		payments.flush();
		if (accepted && !orders.confirmPaid(order.id(), payment.getId())) {
			throw new IllegalStateException("Order " + order.orderNumber() + " could not be confirmed while locked");
		}

		Map<String, Object> details = new HashMap<>();
		details.put("orderId", order.id().toString());
		details.put("amount", payment.amount().toString());
		details.put("method", payment.getMethod());
		if (note != null) {
			details.put("note", note);
		}
		audit.record(AuditRecord.of(actorId, accepted ? AuditAction.PAYMENT_SUCCEEDED : AuditAction.PAYMENT_REFUND_REQUIRED,
				ENTITY, payment.getId()).withNewValue(details));
		outbox.publish(DomainEvent.of(KafkaTopics.PAYMENT_SUCCESS, "PaymentSucceeded", ENTITY, payment.getId(),
				Map.of("paymentId", payment.getId().toString(), "orderId", order.id().toString(),
						"userId", payment.getUserId().toString(), "method", payment.getMethod().name(),
						"amount", payment.amount().amount().toPlainString(),
						"currency", payment.amount().currency().getCurrencyCode(), "refundRequired", !accepted)));
	}

	private PaymentResponse view(Payment payment, String orderNumber) {
		Map<UUID, CompanyBankAccount> accounts = companyAccounts.byId(depositAccountIds(List.of(payment)));
		return view(payment, orderNumber, accounts, companyAccounts.active());
	}

	private static PaymentResponse view(Payment payment, String orderNumber, Map<UUID, CompanyBankAccount> accounts,
			List<CompanyBankAccountResponse> activeAccounts) {
		BankTransferInstructions instructions = payment.getMethod() == PaymentMethod.BANK_TRANSFER
				&& payment.getStatus() == PaymentStatus.PENDING
				? new BankTransferInstructions(payment.getProviderReference(), MoneyResponse.from(payment.amount()),
						activeAccounts)
				: null;
		DepositDetails deposit = null;
		if (payment.hasDepositDetails()) {
			CompanyBankAccount account = accounts.get(payment.getCompanyBankAccountId());
			deposit = new DepositDetails(payment.getDepositMode(), payment.getDepositReference(),
					payment.getDepositSubmittedAt(), payment.getCompanyBankAccountId(),
					account == null ? null : account.getBankName(), account == null ? null : account.getAccountNumber());
		}
		return PaymentResponse.from(payment, orderNumber, instructions, deposit);
	}

	private static List<UUID> depositAccountIds(List<Payment> found) {
		return found.stream().map(Payment::getCompanyBankAccountId).filter(Objects::nonNull).distinct().toList();
	}

	private Payment load(UUID paymentId) {
		return payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private Payment lockPayment(UUID paymentId) {
		return payments.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private static String humanize(PaymentMethod method) {
		return method == PaymentMethod.CARD ? "Card" : "Bank";
	}

	private static String humanize(PaymentStatus status) {
		return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private static Specification<Payment> matching(PaymentSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.method() != null) {
				predicates.add(cb.equal(root.get("method"), c.method()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
