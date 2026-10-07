package com.sealease.backend.order.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.cart.service.CartLine;
import com.sealease.backend.cart.service.CartService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.service.ContainerAllocationService;
import com.sealease.backend.container.service.ContainerService;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.investment.service.OfferingQuery;
import com.sealease.backend.invoice.service.InvoiceRequest;
import com.sealease.backend.invoice.service.InvoiceService;
import com.sealease.backend.kafka.KafkaTopics;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Orders: checkout of the cart, cancellation, expiry and confirmation once paid.
 *
 * <p>Containers follow the order: reserved at checkout (one order line per container), returned to
 * inventory when the order expires or is cancelled, and leased to the investor - each becoming a
 * holding with a monthly payout schedule - when it is paid. Every transition locks the order row,
 * so payment settlement, cancellation and the expiry sweep can never act on the same order
 * concurrently.
 */
@Service
public class OrderService {

	private static final String ENTITY = "ORDER";

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final CartService cart;
	private final OfferingQuery offerings;
	private final ContainerAllocationService inventory;
	private final ContainerService containers;
	private final HoldingService holdings;
	private final PayoutService payouts;
	private final InvoiceService invoices;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;
	private final OrderProperties properties;
	private final Clock clock;

	public OrderService(OrderRepository orders, OrderItemRepository items, CartService cart, OfferingQuery offerings,
			ContainerAllocationService inventory, ContainerService containers, HoldingService holdings,
			PayoutService payouts, InvoiceService invoices, OutboxPublisher outbox,
			AuditService audit, ApplicationEventPublisher events, TransactionTemplate transactions,
			OrderProperties properties, Clock clock) {
		this.orders = orders;
		this.items = items;
		this.cart = cart;
		this.offerings = offerings;
		this.inventory = inventory;
		this.containers = containers;
		this.holdings = holdings;
		this.payouts = payouts;
		this.invoices = invoices;
		this.outbox = outbox;
		this.audit = audit;
		this.events = events;
		this.transactions = transactions;
		this.properties = properties;
		this.clock = clock;
	}

	// ----------------------------------------------------------------------------- checkout

	/**
	 * Turns the cart into an order and reserves its containers, all or nothing. Retrying with the same
	 * idempotency key returns the original order instead of placing a second one.
	 */
	@Transactional
	public OrderResponse checkout(UUID userId, String idempotencyKey, CheckoutRequest request) {
		String key = IdempotencyKey.require(idempotencyKey);
		String hash = requestHash(request);
		Optional<InvestmentOrder> replay = orders.findByIdempotencyKey(userId, key);
		if (replay.isPresent()) {
			if (!replay.get().getRequestHash().equals(hash)) {
				throw new BusinessException(ErrorCode.CONFLICT,
						IdempotencyKey.HEADER + " was already used for a different checkout");
			}
			return view(replay.get(), false);
		}

		List<CartLine> lines = cart.lockForCheckout(userId);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Your cart is empty");
		}
		Set<UUID> accepted = acceptedTerms(request);
		Set<UUID> productIds = lines.stream().map(CartLine::productId).collect(Collectors.toSet());
		if (!accepted.equals(productIds)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Accept the terms of every plan in your cart, and only those");
		}
		Map<UUID, OfferingTerms> terms = offerings.terms(productIds);
		for (UUID productId : productIds) {
			if (!terms.containsKey(productId)) {
				throw new ResourceNotFoundException("Plan", productId);
			}
		}

		for (CartLine line : lines) {
			List<String> problems = offerings.problems(userId, line.productId(), line.quantity());
			if (!problems.isEmpty()) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						terms.get(line.productId()).code() + ": " + String.join("; ", problems));
			}
		}
		Money total = lines.stream()
			.map(l -> terms.get(l.productId()).terms().pricePerContainer().times(BigDecimal.valueOf(l.quantity())))
			.reduce(Money::plus)
			.orElseThrow();
		Instant now = clock.instant();
		InvestmentOrder order = orders.saveAndFlush(new InvestmentOrder("ORD-" + orders.nextNumber(), userId, total,
				key, hash, now.plus(properties.paymentWindow())));

		List<Map<String, Object>> itemEvents = new ArrayList<>();
		for (CartLine line : lines.stream().sorted(Comparator.comparing(CartLine::productId)).toList()) {
			OfferingTerms plan = terms.get(line.productId());
			ProductTerms t = plan.terms();
			// Binding: locks available containers of the type, or fails if there are too few.
			for (ContainerSummary container : inventory.reserve(t.containerType(), line.quantity(), order.getId())) {
				items.save(new OrderItem(order.getId(), line.productId(), plan.code(), t.title(), t.containerType(),
						container.id(), t.pricePerContainer(), t.monthlyRentPercent(), t.monthlyCapitalReturnPercent(),
						t.tenureMonths(), now));
			}
			itemEvents.add(Map.of("productId", line.productId().toString(), "containers", line.quantity()));
		}
		items.flush();
		cart.clear(userId);

		audit.record(AuditRecord.of(userId, AuditAction.ORDER_PLACED, ENTITY, order.getId())
			.withNewValue(Map.of("orderNumber", order.getOrderNumber(), "total", total.toString(),
					"containers", lines.stream().mapToInt(CartLine::quantity).sum())));
		outbox.publish(DomainEvent.of(KafkaTopics.INVESTMENT_CREATED, "InvestmentOrderPlaced", ENTITY, order.getId(),
				Map.of("orderId", order.getId().toString(), "orderNumber", order.getOrderNumber(),
						"userId", userId.toString(), "total", total.amount().toPlainString(),
						"currency", total.currency().getCurrencyCode(), "expiresAt", order.getExpiresAt().toString(),
						"items", itemEvents)));
		return view(order, false);
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
		return view(order, false);
	}

	/**
	 * Expires orders whose payment window has passed, returning their containers to inventory. Each order is handled
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
		inventory.release(order.getId());
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
		return PayableOrder.of(lock(orderId));
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
		return PayableOrder.of(order);
	}

	/**
	 * Called by the payment module, in the transaction that records a successful payment: leases each
	 * reserved container to the investor (a holding with its payout schedule, from today) and issues
	 * the invoice.
	 *
	 * @return {@code false} when the order is no longer awaiting payment (the money must be refunded)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean confirmPaid(UUID orderId, UUID paymentId) {
		InvestmentOrder order = lock(orderId);
		if (!order.isPendingPayment()) {
			return false;
		}
		Instant now = clock.instant();
		List<OrderItem> lines = items.findByOrderIdOrderByProductIdAscIdAsc(orderId);
		List<Map<String, Object>> confirmed = new ArrayList<>();
		List<InvoiceRequest.Line> invoiceLines = new ArrayList<>();
		LocalDate leaseStartsOn = LocalDate.ofInstant(now, ZoneOffset.UTC);
		for (OrderItem item : lines) {
			ContainerSummary container = inventory.lease(item.getContainerId(), orderId);
			PayoutTerms holding = holdings.record(order.getUserId(), item.getProductId(), orderId, item.getId(),
					container.id(), item.amount(), item.getMonthlyRentPercent(), item.getMonthlyCapitalReturnPercent(),
					item.getTenureMonths(), leaseStartsOn, now);
			payouts.schedule(holding);
			confirmed.add(Map.of("holdingId", holding.holdingId().toString(), "productId", item.getProductId().toString(),
					"containerNumber", container.containerNumber(), "amount", item.amount().amount().toPlainString()));
			invoiceLines.add(new InvoiceRequest.Line("%s %s - container %s (%s), %d-month lease".formatted(
					item.getProductCode(), item.getProductTitle(), container.containerNumber(),
					container.containerType().label(), item.getTenureMonths()), item.amount()));
		}
		order.confirm(now);
		orders.flush();
		invoices.issue(new InvoiceRequest(orderId, order.getOrderNumber(), order.getUserId(), paymentId, invoiceLines));

		audit.record(AuditRecord.of(null, AuditAction.ORDER_CONFIRMED, ENTITY, orderId)
			.withNewValue(Map.of("paymentId", paymentId.toString(), "holdings", confirmed.size())));
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
		return withItems(orders.findByUserId(userId, pageable), false);
	}

	@Transactional(readOnly = true)
	public OrderResponse mine(UUID userId, UUID orderId) {
		return orders.findById(orderId)
			.filter(o -> o.getUserId().equals(userId))
			.map(o -> view(o, false))
			.orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	/** For support tickets: the number of one of the user's own orders. */
	@Transactional(readOnly = true)
	public Optional<String> ownedOrderNumber(UUID userId, UUID orderId) {
		return orders.findById(orderId).filter(o -> o.getUserId().equals(userId)).map(InvestmentOrder::getOrderNumber);
	}

	@Transactional(readOnly = true)
	public Page<OrderResponse> search(OrderSearchCriteria criteria, Pageable pageable) {
		return withItems(orders.findAll(matching(criteria), pageable), true);
	}

	@Transactional(readOnly = true)
	public OrderResponse detail(UUID orderId) {
		return view(load(orderId), true);
	}

	@Transactional(readOnly = true)
	public PayableOrder payable(UUID orderId) {
		return PayableOrder.of(load(orderId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, String> orderNumbers(Collection<UUID> orderIds) {
		return orders.findAllById(orderIds).stream()
			.collect(Collectors.toMap(InvestmentOrder::getId, InvestmentOrder::getOrderNumber));
	}

	/** @param staff staff see the reserved containers; investors see theirs once the order is paid */
	private Page<OrderResponse> withItems(Page<InvestmentOrder> page, boolean staff) {
		List<OrderItem> all = items.findByOrderIdIn(page.map(InvestmentOrder::getId).toList());
		Map<UUID, List<OrderItem>> byOrder = all.stream()
			.sorted(Comparator.comparing(OrderItem::getProductId).thenComparing(OrderItem::getId))
			.collect(Collectors.groupingBy(OrderItem::getOrderId));
		Map<UUID, String> numbers = containerNumbers(all);
		return page.map(o -> OrderResponse.from(o, byOrder.getOrDefault(o.getId(), List.of()),
				staff || o.getStatus() == OrderStatus.CONFIRMED ? numbers : Map.of()));
	}

	private OrderResponse view(InvestmentOrder order, boolean staff) {
		List<OrderItem> lines = items.findByOrderIdOrderByProductIdAscIdAsc(order.getId());
		return OrderResponse.from(order, lines,
				staff || order.getStatus() == OrderStatus.CONFIRMED ? containerNumbers(lines) : Map.of());
	}

	private Map<UUID, String> containerNumbers(List<OrderItem> lines) {
		Map<UUID, String> numbers = new HashMap<>();
		containers.summaries(lines.stream().map(OrderItem::getContainerId).toList())
			.forEach((id, c) -> numbers.put(id, c.containerNumber()));
		return numbers;
	}

	private InvestmentOrder load(UUID orderId) {
		return orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	private InvestmentOrder lock(UUID orderId) {
		return orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	private static Set<UUID> acceptedTerms(CheckoutRequest request) {
		Set<UUID> accepted = new HashSet<>();
		for (UUID productId : request.acceptedTerms()) {
			if (!accepted.add(productId)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Plan " + productId + " is listed twice");
			}
		}
		return accepted;
	}

	/** Fingerprint of a checkout request, to tell a genuine retry from a reused idempotency key. */
	private static String requestHash(CheckoutRequest request) {
		String canonical = request.acceptedTerms().stream()
			.sorted()
			.map(UUID::toString)
			.collect(Collectors.joining(";"));
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
			if (c.q() != null && !c.q().isBlank()) {
				String like = "%" + c.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.like(cb.lower(root.get("orderNumber")), like, '\\'));
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
