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
import com.sealease.backend.investment.dto.OfferingTerms;
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.service.CapacityService;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.investment.service.InvestmentAmountPolicy;
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
 * Orders: checkout of the cart, cancellation, expiry and confirmation once paid.
 *
 * <p>Capacity follows the order: reserved at checkout, released when the order expires or is
 * cancelled, committed when it is confirmed. Every transition locks the order row, so payment
 * settlement, cancellation and the expiry sweep can never act on the same order concurrently.
 * Offerings are always locked in id order to rule out deadlocks between checkouts.
 */
@Service
public class OrderService {

	private static final String ENTITY = "ORDER";

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final CartService cart;
	private final OfferingQuery offerings;
	private final CapacityService capacity;
	private final HoldingService holdings;
	private final InvoiceService invoices;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;
	private final OrderProperties properties;
	private final Clock clock;

	public OrderService(OrderRepository orders, OrderItemRepository items, CartService cart, OfferingQuery offerings,
			CapacityService capacity, HoldingService holdings, InvoiceService invoices, OutboxPublisher outbox,
			AuditService audit, ApplicationEventPublisher events, TransactionTemplate transactions,
			OrderProperties properties, Clock clock) {
		this.orders = orders;
		this.items = items;
		this.cart = cart;
		this.offerings = offerings;
		this.capacity = capacity;
		this.holdings = holdings;
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
	 * Turns the cart into an order and reserves its capacity, all or nothing. Retrying with the same
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
			return view(replay.get());
		}

		List<CartLine> lines = cart.lockForCheckout(userId);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Your cart is empty");
		}
		Map<UUID, String> accepted = acceptedTerms(request);
		Set<UUID> productIds = lines.stream().map(CartLine::productId).collect(Collectors.toSet());
		if (!accepted.keySet().equals(productIds)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Accept the terms of every offering in your cart, and only those");
		}
		Map<UUID, OfferingTerms> terms = offerings.terms(productIds);
		for (UUID productId : productIds) {
			OfferingTerms offering = terms.get(productId);
			if (offering == null) {
				throw new ResourceNotFoundException("Offering", productId);
			}
			String current = offering.terms().termsVersion();
			if (!current.equals(accepted.get(productId))) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The terms of " + offering.code()
						+ " are now version " + current + "; review and accept them again");
			}
		}

		Money total = lines.stream().map(CartLine::amount).reduce(Money::plus).orElseThrow();
		Instant now = clock.instant();
		InvestmentOrder order = orders.saveAndFlush(new InvestmentOrder("ORD-" + orders.nextNumber(), userId, total,
				key, hash, now.plus(properties.paymentWindow())));

		List<Map<String, Object>> itemEvents = new ArrayList<>();
		for (CartLine line : lines.stream().sorted(Comparator.comparing(CartLine::productId)).toList()) {
			OfferingTerms offering = terms.get(line.productId());
			ProductTerms t = offering.terms();
			// Re-validates eligibility, amount rules and availability under the offering's row lock.
			capacity.reserve(line.productId(), userId, line.amount(),
					OrderItem.capacityReference(order.getId(), line.productId()));
			items.save(new OrderItem(order.getId(), line.productId(), offering.code(), t.title(), t.investmentType(),
					line.amount(), InvestmentAmountPolicy.ownershipPercent(t, line.amount()), t.termsVersion(),
					InvestmentAmountPolicy.rentalShare(t, line.amount()), t.rentalFrequency(), t.durationMonths(), now));
			itemEvents.add(Map.of("productId", line.productId().toString(),
					"amount", line.amount().amount().toPlainString()));
		}
		items.flush();
		cart.clear(userId);

		audit.record(AuditRecord.of(userId, AuditAction.ORDER_PLACED, ENTITY, order.getId())
			.withNewValue(Map.of("orderNumber", order.getOrderNumber(), "total", total.toString(),
					"items", lines.size())));
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
	 * Expires orders whose payment window has passed, releasing their capacity. Each order is handled
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
		for (OrderItem item : items.findByOrderIdOrderByProductId(order.getId())) {
			capacity.release(item.getProductId(), item.getCapacityReference());
		}
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
	 * Called by the payment module, in the transaction that records a successful payment: commits the
	 * reserved capacity, creates the holdings and issues the invoice.
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
		List<OrderItem> lines = items.findByOrderIdOrderByProductId(orderId);
		List<Map<String, Object>> confirmed = new ArrayList<>();
		List<InvoiceRequest.Line> invoiceLines = new ArrayList<>();
		for (OrderItem item : lines) {
			capacity.commit(item.getProductId(), item.getCapacityReference());
			UUID holdingId = holdings.record(order.getUserId(), item.getProductId(), orderId, item.getId(), item.amount(),
					item.getOwnershipPercent(), item.getTermsVersion(), now);
			confirmed.add(Map.of("holdingId", holdingId.toString(), "productId", item.getProductId().toString(),
					"amount", item.amount().amount().toPlainString(),
					"ownershipPercent", item.getOwnershipPercent().toPlainString()));
			invoiceLines.add(new InvoiceRequest.Line("%s %s - %s%% ownership".formatted(item.getProductCode(),
					item.getProductTitle(), item.getOwnershipPercent().stripTrailingZeros().toPlainString()),
					item.amount()));
		}
		order.confirm(now);
		orders.flush();
		invoices.issue(new InvoiceRequest(orderId, order.getOrderNumber(), order.getUserId(), paymentId, invoiceLines));

		audit.record(AuditRecord.of(null, AuditAction.ORDER_CONFIRMED, ENTITY, orderId)
			.withNewValue(Map.of("paymentId", paymentId.toString(), "holdings", confirmed.size())));
		Money total = order.total();
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
			.map(this::view)
			.orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
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
		return PayableOrder.of(load(orderId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, String> orderNumbers(Collection<UUID> orderIds) {
		return orders.findAllById(orderIds).stream()
			.collect(Collectors.toMap(InvestmentOrder::getId, InvestmentOrder::getOrderNumber));
	}

	private Page<OrderResponse> withItems(Page<InvestmentOrder> page) {
		Map<UUID, List<OrderItem>> byOrder = items.findByOrderIdIn(page.map(InvestmentOrder::getId).toList()).stream()
			.sorted(Comparator.comparing(OrderItem::getProductId))
			.collect(Collectors.groupingBy(OrderItem::getOrderId));
		return page.map(o -> OrderResponse.from(o, byOrder.getOrDefault(o.getId(), List.of())));
	}

	private OrderResponse view(InvestmentOrder order) {
		return OrderResponse.from(order, items.findByOrderIdOrderByProductId(order.getId()));
	}

	private InvestmentOrder load(UUID orderId) {
		return orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	private InvestmentOrder lock(UUID orderId) {
		return orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	private static Map<UUID, String> acceptedTerms(CheckoutRequest request) {
		Map<UUID, String> accepted = new HashMap<>();
		Set<UUID> seen = new HashSet<>();
		for (CheckoutRequest.AcceptedTerms terms : request.acceptedTerms()) {
			if (!seen.add(terms.productId())) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Offering " + terms.productId() + " is listed twice");
			}
			accepted.put(terms.productId(), terms.termsVersion().strip());
		}
		return accepted;
	}

	/** Fingerprint of a checkout request, to tell a genuine retry from a reused idempotency key. */
	private static String requestHash(CheckoutRequest request) {
		String canonical = request.acceptedTerms().stream()
			.sorted(Comparator.comparing(CheckoutRequest.AcceptedTerms::productId))
			.map(t -> t.productId() + "=" + t.termsVersion().strip())
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
