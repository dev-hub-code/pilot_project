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
import com.sealease.backend.payment.dto.ConfirmTransferRequest;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.PaymentSearchCriteria;
import com.sealease.backend.payment.dto.RefundRequest;
import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;
import com.sealease.backend.payment.provider.BankTransferProperties;
import com.sealease.backend.payment.provider.PaymentProvider;
import com.sealease.backend.payment.provider.ProviderEvent;
import com.sealease.backend.payment.provider.SimulatedCardProvider;
import com.sealease.backend.payment.provider.WebhookPaymentProvider;
import com.sealease.backend.payment.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	private static final String INSERT_EVENT = """
			INSERT INTO payment_events (id, provider, provider_event_id, payment_id, event_type, payload, received_at)
			VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
			ON CONFLICT (provider, provider_event_id) DO NOTHING
			""";

	private final PaymentRepository payments;
	private final OrderService orders;
	private final Map<PaymentMethod, PaymentProvider> providersByMethod = new EnumMap<>(PaymentMethod.class);
	private final Map<String, WebhookPaymentProvider> webhookProviders = new HashMap<>();
	private final Optional<SimulatedCardProvider> simulator;
	private final BankTransferProperties bankAccount;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public PaymentService(PaymentRepository payments, OrderService orders, List<PaymentProvider> providers,
			Optional<SimulatedCardProvider> simulator, BankTransferProperties bankAccount, OutboxPublisher outbox,
			AuditService audit, JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock) {
		this.payments = payments;
		this.orders = orders;
		for (PaymentProvider provider : providers) {
			if (providersByMethod.putIfAbsent(provider.method(), provider) != null) {
				throw new IllegalStateException("More than one payment provider for " + provider.method());
			}
			if (provider instanceof WebhookPaymentProvider webhook) {
				webhookProviders.put(webhook.name(), webhook);
			}
		}
		this.simulator = simulator;
		this.bankAccount = bankAccount;
		this.outbox = outbox;
		this.audit = audit;
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	// --------------------------------------------------------------------------- investor

	/**
	 * Starts paying an order. Idempotent per key; asking again for the method already in flight
	 * resumes that payment, and switching method cancels the attempt in flight.
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
		if (provider == null) {
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

	@Transactional(readOnly = true)
	public List<PaymentResponse> forOrder(UUID orderId, UUID ownerId) {
		PayableOrder order = orders.payable(orderId);
		if (ownerId != null && !order.userId().equals(ownerId)) {
			throw new ResourceNotFoundException("Order", orderId);
		}
		return payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
			.map(p -> view(p, order.orderNumber()))
			.toList();
	}

	/** Development only: makes the simulated card gateway report an outcome for a payment. */
	@Transactional
	public PaymentResponse simulate(UUID userId, UUID paymentId, ProviderEvent.Outcome outcome) {
		SimulatedCardProvider gateway = simulator.orElseThrow(() -> new ResourceNotFoundException("Payment simulator", "card"));
		Payment payment = load(paymentId);
		if (!payment.getUserId().equals(userId)) {
			throw new ResourceNotFoundException("Payment", paymentId);
		}
		if (!SimulatedCardProvider.NAME.equals(payment.getProvider()) || payment.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only pending simulated card payments");
		}
		SimulatedCardProvider.SignedEvent delivery = gateway.signedEvent(payment.getProviderReference(), payment.amount(),
				outcome);
		receiveWebhook(SimulatedCardProvider.NAME, delivery.body(), delivery.signature());
		return view(load(paymentId), orders.payable(payment.getOrderId()).orderNumber());
	}

	// ----------------------------------------------------------------------------- webhooks

	/**
	 * Handles a provider notification. Redeliveries (same provider event id) are ignored, so the
	 * provider may retry as often as it likes.
	 */
	@Transactional
	public void receiveWebhook(String providerName, byte[] body, String signature) {
		WebhookPaymentProvider provider = webhookProviders.get(providerName);
		if (provider == null) {
			throw new ResourceNotFoundException("Payment provider", providerName);
		}
		ProviderEvent event = provider.verify(body, signature);
		Optional<Payment> payment = payments.findByProviderAndProviderReference(providerName, event.providerReference());
		int recorded = jdbc.update(INSERT_EVENT, UUID.randomUUID(), providerName, event.eventId(),
				payment.map(Payment::getId).orElse(null), event.outcome().name(),
				jsonMapper.writeValueAsString(event.payload()), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		if (recorded == 0) {
			log.debug("Ignoring redelivered {} event {}", providerName, event.eventId());
			return;
		}
		if (payment.isEmpty()) {
			log.warn("{} event {} refers to unknown payment {}", providerName, event.eventId(), event.providerReference());
			return;
		}

		PayableOrder order = orders.lockForPayment(payment.get().getOrderId());
		Payment locked = lockPayment(payment.get().getId());
		if (event.outcome() == ProviderEvent.Outcome.SUCCEEDED) {
			Money received = event.amount() == null || event.currency() == null ? null
					: Money.of(event.amount().toPlainString(), event.currency());
			settle(locked, order, received, event.eventId(), null);
		}
		else if (locked.getStatus() == PaymentStatus.PENDING) {
			String reason = event.failureReason() == null ? "Declined by the provider" : event.failureReason();
			locked.fail(reason);
			payments.flush();
			audit.record(AuditRecord.of(null, AuditAction.PAYMENT_FAILED, ENTITY, locked.getId())
				.withNewValue(Map.of("reason", reason)));
			outbox.publish(DomainEvent.of(KafkaTopics.PAYMENT_FAILED, "PaymentFailed", ENTITY, locked.getId(),
					Map.of("paymentId", locked.getId().toString(), "orderId", locked.getOrderId().toString(),
							"userId", locked.getUserId().toString(), "reason", reason)));
		}
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
		return page.map(p -> view(p, numbers.get(p.getOrderId())));
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
		BankTransferInstructions instructions = payment.getMethod() == PaymentMethod.BANK_TRANSFER
				&& payment.getStatus() == PaymentStatus.PENDING
				? new BankTransferInstructions(bankAccount.beneficiaryName(), bankAccount.iban(), bankAccount.bic(),
						bankAccount.bankName(), payment.getProviderReference(), MoneyResponse.from(payment.amount()))
				: null;
		return PaymentResponse.from(payment, orderNumber, instructions,
				SimulatedCardProvider.NAME.equals(payment.getProvider()));
	}

	private Payment load(UUID paymentId) {
		return payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private Payment lockPayment(UUID paymentId) {
		return payments.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private static String humanize(PaymentMethod method) {
		return method == PaymentMethod.CARD ? "Card" : "Bank transfer";
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
