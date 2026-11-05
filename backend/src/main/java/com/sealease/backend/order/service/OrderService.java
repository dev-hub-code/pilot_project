package com.sealease.backend.order.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.investment.service.OfferingQuery;
import com.sealease.backend.invoice.service.InvoiceRequest;
import com.sealease.backend.invoice.service.InvoiceService;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.earning.service.RentCreditSettingsService;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.order.dto.CheckoutRequest;
import com.sealease.backend.order.dto.OrderResponse;
import com.sealease.backend.order.dto.OrderSearchCriteria;
import com.sealease.backend.order.dto.PayableOrder;
import com.sealease.backend.order.entity.InvestmentOrder;
import com.sealease.backend.order.entity.OrderItem;
import com.sealease.backend.order.entity.OrderStatus;
import com.sealease.backend.order.event.OrderClosedEvent;
import com.sealease.backend.order.event.OrderConfirmedEvent;
import com.sealease.backend.order.repository.OrderItemRepository;
import com.sealease.backend.order.repository.OrderRepository;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import jakarta.persistence.criteria.Predicate;
import com.sealease.backend.referral.dto.ReferralScheme;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orders: buying units of a plan, cancellation, expiry and confirmation once paid.
 *
 * <p>One order line per unit. Checkout reserves no stock: when the order is paid, each line becomes a
 * holding with its investment agreement, which the investor accepts to start the lease and payouts. Every transition
 * locks the order row, so payment settlement, cancellation and the expiry sweep can never act on the same
 * order concurrently.
 */
@Service
public class OrderService {

	private static final String ENTITY = "ORDER";

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final OfferingQuery offerings;
	private final HoldingService holdings;
	private final PayoutService payouts;
	private final InvoiceService invoices;
	private final LeaseAgreementIssuer agreements;
	private final NotificationService notifications;
	private final RentCreditSettingsService credits;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;
	private final OrderProperties properties;
	private final Clock clock;

	public OrderService(OrderRepository orders, OrderItemRepository items, OfferingQuery offerings,
			HoldingService holdings,
			PayoutService payouts, InvoiceService invoices, LeaseAgreementIssuer agreements, NotificationService notifications,
			RentCreditSettingsService credits, OutboxPublisher outbox,
			AuditService audit, ApplicationEventPublisher events, TransactionTemplate transactions,
			OrderProperties properties, Clock clock) {
		this.orders = orders;
		this.items = items;
		this.offerings = offerings;
		this.holdings = holdings;
		this.payouts = payouts;
		this.invoices = invoices;
		this.agreements = agreements;
		this.notifications = notifications;
		this.credits = credits;
		this.outbox = outbox;
		this.audit = audit;
		this.events = events;
		this.transactions = transactions;
		this.properties = properties;
		this.clock = clock;
	}

	// ----------------------------------------------------------------------------- checkout

	/**
	 * Places an order for units of a plan. Retrying with the same idempotency key returns the original order instead of
	 * placing a second one, even when the two arrive together.
	 */
	@Transactional
	public OrderResponse checkout(UUID userId, String idempotencyKey, CheckoutRequest request) {
		String key = IdempotencyKey.require(idempotencyKey);
		String hash = requestHash(request);
		orders.lockIdempotencyKey(userId + ":" + key);
		Optional<InvestmentOrder> replay = orders.findByIdempotencyKey(userId, key);
		if (replay.isPresent()) {
			if (!replay.get().getRequestHash().equals(hash)) {
				throw new BusinessException(ErrorCode.CONFLICT,
						IdempotencyKey.HEADER + " was already used for a different checkout");
			}
			return view(replay.get());
		}

		OfferingTerms plan = offerings.terms(request.productId());
		int units = request.units();
		List<String> problems = offerings.problems(userId, plan.id(), units);
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, String.join("; ", problems));
		}
		ProductTerms t = plan.terms();
		Money total = t.amountFor(units);
		Instant now = clock.instant();
		InvestmentOrder order = orders.saveAndFlush(new InvestmentOrder("ORD-" + orders.nextNumber(), userId, total,
				key, hash, now.plus(properties.paymentWindow())));
		// The terms are fixed on the order: a plan changing or closing later leaves them as they were.
		items.saveAndFlush(new OrderItem(order.getId(), plan.id(), plan.code(), t.title(), total, units, t.unitPrice(),
				t.monthlyReturnPercent(), t.monthlyCapitalReturnPercent(), ProductTerms.monthsOf(t.tenureYears()), now));
		List<Map<String, Object>> itemEvents = List.of(Map.of("productId", plan.id().toString(),
				"amount", total.amount().toPlainString(), "units", units, "tenureYears", t.tenureYears()));

		audit.record(AuditRecord.of(userId, AuditAction.ORDER_PLACED, ENTITY, order.getId())
			.withNewValue(Map.of("orderNumber", order.getOrderNumber(), "total", total.toString(),
					"plan", plan.code(), "units", units)));
		outbox.publish(DomainEvent.of(KafkaTopics.INVESTMENT_CREATED, "InvestmentOrderPlaced", ENTITY, order.getId(),
				Map.of("orderId", order.getId().toString(), "orderNumber", order.getOrderNumber(),
						"userId", userId.toString(), "total", total.amount().toPlainString(),
						"currency", total.currency().getCurrencyCode(), "expiresAt", order.getExpiresAt().toString(),
						"items", itemEvents)));
		return view(order);
	}

	// ------------------------------------------------------------------------ cancel / expire

	@Transactional
	public OrderResponse cancel(UUID userId, UUID orderId) {
		InvestmentOrder order = lock(orderId);
		if (!order.getUserId().equals(userId)) {
			throw new ResourceNotFoundException("Order", orderId);
		}
		if (!order.isPendingPayment()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Only orders awaiting payment can be cancelled");
		}
		close(order, OrderStatus.CANCELLED, "Cancelled by the investor", userId, AuditAction.ORDER_CANCELLED);
		return view(order);
	}

	/**
	 * Expires orders whose payment window has passed, Each order is handled
	 * in its own transaction, so one failure does not hold back the rest.
	 *
	 * @return the number of orders expired
	 */
	public int expireDue() {
		List<UUID> due = orders.findDueForExpiry(clock.instant(), PageRequest.of(0, properties.expiryBatchSize()));
		int expired = 0;
		for (UUID orderId : due) {
			if (Boolean.TRUE.equals(transactions.execute(status -> expire(orderId)))) {
				expired++;
			}
		}
		return expired;
	}

	private boolean expire(UUID orderId) {
		InvestmentOrder order = lock(orderId);
		// Re-checked under the lock: a payment may have confirmed the order since it was selected.
		if (!order.isPendingPayment() || order.getExpiresAt().isAfter(clock.instant())) {
			return false;
		}
		close(order, OrderStatus.EXPIRED, "Payment was not received in time", null, AuditAction.ORDER_EXPIRED);
		return true;
	}

	private void close(InvestmentOrder order, OrderStatus outcome, String reason, UUID actorId, AuditAction action) {
		order.close(outcome, reason, clock.instant());
		orders.flush();
		events.publishEvent(new OrderClosedEvent(order.getId(), outcome));
		audit.record(AuditRecord.of(actorId, action, ENTITY, order.getId())
			.withOldValue(Map.of("status", OrderStatus.PENDING_PAYMENT))
			.withNewValue(Map.of("status", outcome, "reason", reason)));
	}

	// ------------------------------------------------------------------------- confirmation

	/** Locks the order for a payment decision (start, settle) within the caller's transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public PayableOrder lockForPayment(UUID orderId) {
		return payable(lock(orderId));
	}

	/**
	 * Called by the payment module once an investor has submitted the details of a bank payment: the
	 * reservation is kept until finance has verified the money.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public PayableOrder holdForVerification(UUID orderId, Instant until) {
		InvestmentOrder order = lock(orderId);
		order.holdUntil(until);
		orders.flush();
		return payable(order);
	}

	/**
	 * Called by the payment module, in the transaction that records a successful payment: each line becomes a holding
	 * with its investment agreement to accept (accepting starts the returns), and the invoice is issued.
	 *
	 * @param paidAtOffice the order was paid in cash at the office
	 * @return {@code false} when the order is no longer awaiting payment (the money must be refunded)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean confirmPaid(UUID orderId, UUID paymentId, boolean paidAtOffice) {
		InvestmentOrder order = lock(orderId);
		if (!order.isPendingPayment()) {
			return false;
		}
		Instant now = clock.instant();
		List<OrderItem> lines = items.findByOrderIdOrderByProductIdAscIdAsc(orderId);
		List<Map<String, Object>> confirmed = new ArrayList<>();
		List<InvoiceRequest.Line> invoiceLines = new ArrayList<>();
		for (OrderItem item : lines) {
			UUID holdingId = holdings.recordAwaitingAcceptance(order.getUserId(), item.getProductId(), orderId, item.getId(),
					item.amount(), item.getMonthlyRentPercent(), item.getMonthlyCapitalReturnPercent(),
					item.getTenureMonths(), now, paidAtOffice);
			LeaseAgreementResponse agreement = agreements.issue(null, holdingId, order.getUserId(), order.getOrderNumber());
			notifications.notify(order.getUserId(), "AGREEMENT_ISSUED", "Your investment is confirmed",
					item.getProductCode() + ": accept your investment agreement " + agreement.agreementNumber()
							+ " to start your returns. They are credited to your wallet " + credits.current().label()
							+ " from then on.", "/portfolio/" + holdingId + "/agreement");
			confirmed.add(Map.of("holdingId", holdingId.toString(), "productId", item.getProductId().toString(),
					"amount", item.amount().amount().toPlainString()));
			invoiceLines.add(new InvoiceRequest.Line("%s %s - %d-month investment".formatted(item.getProductCode(),
					item.getProductTitle(), item.getTenureMonths()), item.amount()));
		}
		order.confirm(now);
		orders.flush();
		invoices.issue(new InvoiceRequest(orderId, order.getOrderNumber(), order.getUserId(), paymentId, invoiceLines));

		Map<String, Object> audited = new HashMap<>(Map.of("paymentId", paymentId.toString(),
				"holdings", confirmed.size()));
		audit.record(AuditRecord.of(null, AuditAction.ORDER_CONFIRMED, ENTITY, orderId).withNewValue(audited));
		Money total = order.total();
		events.publishEvent(new OrderConfirmedEvent(orderId, order.getOrderNumber(), order.getUserId(), total));
		outbox.publish(DomainEvent.of(KafkaTopics.INVESTMENT_CONFIRMED, "InvestmentConfirmed", ENTITY, orderId,
				Map.of("orderId", orderId.toString(), "orderNumber", order.getOrderNumber(),
						"userId", order.getUserId().toString(), "paymentId", paymentId.toString(),
						"total", total.amount().toPlainString(), "currency", total.currency().getCurrencyCode(),
						"holdings", confirmed)));
		return true;
	}

	// ------------------------------------------------------------------------------ queries

	@Transactional(readOnly = true)
	public Page<OrderResponse> mine(UUID userId, Pageable pageable) {
		return withItems(orders.findByUserId(userId, pageable));
	}

	@Transactional(readOnly = true)
	public OrderResponse mine(UUID userId, UUID orderId) {
		return orders.findById(orderId)
			.filter(o -> o.getUserId().equals(userId))
			.map(o -> view(o))
			.orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	/** For support tickets: the number of one of the user's own orders. */
	@Transactional(readOnly = true)
	public Optional<String> ownedOrderNumber(UUID userId, UUID orderId) {
		return orders.findById(orderId).filter(o -> o.getUserId().equals(userId)).map(InvestmentOrder::getOrderNumber);
	}

	@Transactional(readOnly = true)
	public Page<OrderResponse> search(OrderSearchCriteria criteria, Pageable pageable) {
		return withItems(orders.findAll(matching(criteria), pageable));
	}

	@Transactional(readOnly = true)
	public OrderResponse detail(UUID orderId) {
		return view(load(orderId));
	}

	@Transactional(readOnly = true)
	public PayableOrder payable(UUID orderId) {
		return payable(load(orderId));
	}

	private PayableOrder payable(InvestmentOrder order) {
		return PayableOrder.of(order);
	}

	/** The order with this whole order number ("ORD-…"), any case. */
	@Transactional(readOnly = true)
	public Optional<UUID> idByNumber(String orderNumber) {
		return orderNumber == null || orderNumber.isBlank() ? Optional.empty() : orders.findIdByOrderNumber(orderNumber.strip());
	}

	@Transactional(readOnly = true)
	public Map<UUID, String> orderNumbers(Collection<UUID> orderIds) {
		return orders.findAllById(orderIds).stream()
			.collect(Collectors.toMap(InvestmentOrder::getId, InvestmentOrder::getOrderNumber));
	}

	private Page<OrderResponse> withItems(Page<InvestmentOrder> page) {
		List<OrderItem> all = items.findByOrderIdIn(page.map(InvestmentOrder::getId).toList());
		Map<UUID, List<OrderItem>> byOrder = all.stream()
			.sorted(Comparator.comparing(OrderItem::getProductId).thenComparing(OrderItem::getId))
			.collect(Collectors.groupingBy(OrderItem::getOrderId));
		return page.map(o -> OrderResponse.from(o, byOrder.getOrDefault(o.getId(), List.of())));
	}

	private OrderResponse view(InvestmentOrder order) {
		return OrderResponse.from(order, items.findByOrderIdOrderByProductIdAscIdAsc(order.getId()));
	}

	private InvestmentOrder load(UUID orderId) {
		return orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	private InvestmentOrder lock(UUID orderId) {
		return orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	/** Fingerprint of a checkout request, to tell a genuine retry from a reused idempotency key. */
	private static String requestHash(CheckoutRequest request) {
		String canonical = request.productId() + ";" + request.units();
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	private static Specification<InvestmentOrder> matching(OrderSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.exact()) {
				// Without browsing: only a whole order number, or the orders of the investor the search names.
				if (c.q() == null || c.q().isBlank()) {
					return cb.disjunction();
				}
				Predicate number = cb.equal(cb.lower(root.get("orderNumber")), c.q().strip().toLowerCase(Locale.ROOT));
				predicates.add(c.qInvestorId() == null ? number : cb.or(number, cb.equal(root.get("userId"), c.qInvestorId())));
			}
			else if (c.q() != null && !c.q().isBlank()) {
				String like = "%" + c.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				Predicate number = cb.like(cb.lower(root.get("orderNumber")), like, '\\');
				predicates.add(c.qInvestorId() == null ? number : cb.or(number, cb.equal(root.get("userId"), c.qInvestorId())));
			}
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.userId() != null) {
				predicates.add(cb.equal(root.get("userId"), c.userId()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
