package com.sealease.backend.payment.service;

import java.util.Currency;
import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.common.api.Assignee;
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
import com.sealease.backend.payment.dto.OfficeCashInstructions;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.PaymentSearchCriteria;
import com.sealease.backend.payment.dto.RefundRequest;
import com.sealease.backend.payment.dto.SubmitDepositRequest;
import com.sealease.backend.payment.dto.SubmitReceiptRequest;
import com.sealease.backend.payment.entity.CompanyBankAccount;
import com.sealease.backend.payment.entity.DepositMode;
import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;
import com.sealease.backend.payment.dto.CheckoutInstructions;
import com.sealease.backend.payment.dto.VirtualAccountInstructions;
import com.sealease.backend.payment.gateway.Paise;
import com.sealease.backend.payment.gateway.RazorpayClient.RazorpayException;
import com.sealease.backend.payment.gateway.RazorpayCredentials;
import com.sealease.backend.payment.gateway.RazorpayKeys;
import com.sealease.backend.payment.provider.BankTransferProperties;
import com.sealease.backend.payment.provider.OfficeProperties;
import com.sealease.backend.payment.provider.PaymentProvider;
import com.sealease.backend.payment.repository.PaymentRepository;
import com.sealease.backend.payment.repository.PaymentRepository.PaymentKeys;
import jakarta.persistence.criteria.Predicate;
import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.referral.service.ReferralSchemeService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
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
	private final OfficeProperties office;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final UserAccountService users;
	private final RazorpayCredentials razorpay;
	private final ReferralSchemeService schemes;
	private final UnmatchedGatewayReceipts unmatched;
	private final TransactionTemplate transactions;
	private final DepositLedger depositLedger;
	private final ReinvestmentLedger reinvestment;
	private final PaymentGatewaySettingsService gatewaySettings;
	private final Clock clock;

	public PaymentService(PaymentRepository payments, OrderService orders, List<PaymentProvider> providers,
			CompanyBankAccountService companyAccounts, BankTransferProperties bankTransfer, OfficeProperties office,
			OutboxPublisher outbox, AuditService audit, UserAccountService users, RazorpayCredentials razorpay,
			ReferralSchemeService schemes, UnmatchedGatewayReceipts unmatched, TransactionTemplate transactions,
			DepositLedger depositLedger, ReinvestmentLedger reinvestment, PaymentGatewaySettingsService gatewaySettings,
			Clock clock) {
		this.payments = payments;
		this.orders = orders;
		for (PaymentProvider provider : providers) {
			if (providersByMethod.putIfAbsent(provider.method(), provider) != null) {
				throw new IllegalStateException("More than one payment provider for " + provider.method());
			}
		}
		this.companyAccounts = companyAccounts;
		this.bankTransfer = bankTransfer;
		this.office = office;
		this.outbox = outbox;
		this.audit = audit;
		this.users = users;
		this.razorpay = razorpay;
		this.schemes = schemes;
		this.unmatched = unmatched;
		this.transactions = transactions;
		this.depositLedger = depositLedger;
		this.reinvestment = reinvestment;
		this.gatewaySettings = gatewaySettings;
		this.clock = clock;
	}

	// --------------------------------------------------------------------------- investor

	/**
	 * How this investor pays. Online investors pay through the gateway (its checkout, or a virtual account for a bank
	 * transfer), confirmed automatically, or from their available balance; network investors pay by bank transfer or in
	 * cash at the office, confirmed by finance. Online investors never pay in cash: until the gateway is set up, they pay
	 * by bank transfer (or from their balance).
	 */
	@Transactional(readOnly = true)
	public List<PaymentMethod> methodsFor(UUID userId) {
		if (schemes.schemeOf(userId) == ReferralScheme.ONLINE) {
			GatewaySettings gateway = gatewaySettings.current();
			if (!gateway.checkoutOffered()) {
				return List.of(PaymentMethod.BANK_TRANSFER, PaymentMethod.WALLET);
			}
			// Bank transfer into a virtual account needs Smart Collect; without it, the checkout only.
			return gateway.virtualAccountsOffered()
					? List.of(PaymentMethod.GATEWAY, PaymentMethod.VIRTUAL_ACCOUNT, PaymentMethod.WALLET)
					: List.of(PaymentMethod.GATEWAY, PaymentMethod.WALLET);
		}
		return List.of(PaymentMethod.BANK_TRANSFER, PaymentMethod.OFFICE_CASH);
	}

	/**
	 * The most one checkout payment can be, when this investor pays through the checkout and the gateway has a limit;
	 * otherwise null. Larger orders are paid another way.
	 */
	@Transactional(readOnly = true)
	public Money checkoutLimit(UUID userId, Currency currency) {
		GatewaySettings gateway = gatewaySettings.current();
		return methodsFor(userId).contains(PaymentMethod.GATEWAY) && gateway.hasCheckoutLimit()
				? Money.of(gateway.maxAmount(), currency) : null;
	}

	/**
	 * Starts paying an order in one of the investor's {@link #methodsFor ways to pay}. Idempotent per key; asking again
	 * while a payment of the same method is in flight resumes it, and one of another method still in flight is
	 * cancelled. Paying at the office, through the gateway's checkout or into a virtual account holds the order for that
	 * method's window, as the investor needs the time to pay.
	 *
	 * <p>Not one transaction: the payment provider (the gateway, over the network) is called with no lock held, so a
	 * slow gateway does not hold up the order's cancellation, webhooks or expiry. The request is checked under the order
	 * lock, the provider called, and the payment recorded under the lock after checking again. Whatever the provider
	 * opened is closed when it ends up unused (the order changed meanwhile, or another request won).
	 */
	public PaymentResponse start(UUID userId, UUID orderId, PaymentMethod method, String idempotencyKey) {
		String key = IdempotencyKey.require(idempotencyKey);
		if (method == PaymentMethod.WALLET) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Paying from your available balance needs your password");
		}
		Started checked = transactions.execute(status -> check(userId, orderId, method, key));
		if (checked.existing() != null) {
			return checked.existing();
		}
		PaymentProvider provider = providersByMethod.get(method);
		PaymentProvider.Opened opened;
		try {
			opened = provider.open(new PaymentProvider.OpenRequest(checked.orderNumber(), checked.due(), checked.payBy()));
		}
		catch (RazorpayException e) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, e.getMessage());
		}
		boolean recorded = false;
		try {
			Started done = transactions.execute(status -> record(userId, orderId, method, key, opened));
			recorded = done.existing() == null;
			return recorded ? done.recorded() : done.existing();
		}
		finally {
			if (!recorded) {
				closeQuietly(provider, opened.reference());
			}
		}
	}

	/**
	 * The outcome of checking a start request under the order lock: an {@code existing} payment to answer with, or what
	 * to open at the provider; after recording, the {@code recorded} payment.
	 */
	private record Started(PaymentResponse existing, String orderNumber, Money due, Instant payBy,
			PaymentResponse recorded) {

		static Started answer(PaymentResponse existing) {
			return new Started(existing, null, null, null, null);
		}

	}

	/** Checks the request under the order lock, changing nothing: what to open at the provider, or the answer. */
	private Started check(UUID userId, UUID orderId, PaymentMethod method, String key) {
		PayableOrder order = orders.lockForPayment(orderId);
		Optional<PaymentResponse> answer = validateStart(userId, order, method, key);
		if (answer.isPresent()) {
			return Started.answer(answer.get());
		}
		Instant holdUntil = holdUntil(method, clock.instant());
		return new Started(null, order.orderNumber(), order.total(), holdUntil != null ? holdUntil : order.expiresAt(),
				null);
	}

	/** Records the payment the provider opened, after checking the request again under the order lock. */
	private Started record(UUID userId, UUID orderId, PaymentMethod method, String key, PaymentProvider.Opened opened) {
		PayableOrder order = orders.lockForPayment(orderId);
		Optional<PaymentResponse> answer = validateStart(userId, order, method, key);
		if (answer.isPresent()) {
			return Started.answer(answer.get()); // Another request won meanwhile: ours is closed.
		}
		for (Payment inFlight : payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
			cancelPending(inFlight, method == PaymentMethod.OFFICE_CASH ? "Replaced by a cash payment at the office"
					: "Replaced by a " + humanize(method).toLowerCase(Locale.ROOT) + " payment");
		}
		payments.flush();

		Money due = order.total();
		Instant holdUntil = holdUntil(method, clock.instant());
		PaymentProvider provider = providersByMethod.get(method);
		Payment created = new Payment(orderId, userId, method, provider.name(), opened.reference(), due, key);
		if (opened.virtualAccount() != null) {
			created.recordVirtualAccount(opened.virtualAccount());
		}
		Payment payment = payments.saveAndFlush(created);
		if (holdUntil != null) {
			order = orders.holdForVerification(orderId, holdUntil);
		}
		audit.record(AuditRecord.of(userId, AuditAction.PAYMENT_STARTED, ENTITY, payment.getId())
			.withNewValue(Map.of("orderId", orderId.toString(), "method", method, "amount", due.toString(),
					"orderExpiresAt", order.expiresAt().toString())));
		return new Started(null, null, null, null, view(payment, order.orderNumber()));
	}

	/**
	 * The start request's rules, checked against the locked order: the answer when it is a replay or resumes a payment
	 * in flight, empty when a payment is to be opened; refused otherwise.
	 */
	private Optional<PaymentResponse> validateStart(UUID userId, PayableOrder order, PaymentMethod method, String key) {
		UUID orderId = order.id();
		if (!order.userId().equals(userId)) {
			throw new ResourceNotFoundException("Order", orderId);
		}
		Optional<Payment> replay = payments.findByOrderIdAndIdempotencyKey(orderId, key);
		if (replay.isPresent()) {
			if (replay.get().getMethod() != method) {
				throw new BusinessException(ErrorCode.CONFLICT,
						IdempotencyKey.HEADER + " was already used for a different payment");
			}
			return Optional.of(view(replay.get(), order.orderNumber()));
		}
		if (!order.acceptsPaymentAt(clock.instant())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, order.status() == OrderStatus.PENDING_PAYMENT
					? "The payment window for this order has passed" : "This order is no longer awaiting payment");
		}
		PaymentProvider provider = providersByMethod.get(method);
		if (method == PaymentMethod.CARD) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Card payments are not accepted; please pay by bank or at our office");
		}
		List<PaymentMethod> allowed = methodsFor(userId);
		boolean cashAllowed = allowed.contains(PaymentMethod.OFFICE_CASH);
		if (!allowed.contains(method)) {
			String reason;
			if (method == PaymentMethod.VIRTUAL_ACCOUNT && allowed.contains(PaymentMethod.GATEWAY)) {
				reason = "Bank transfer isn't available right now; please pay online";
			}
			else if (allowed.contains(PaymentMethod.GATEWAY)) {
				reason = "Please pay online, through the checkout or by bank transfer to your payment's own account";
			}
			else if (method == PaymentMethod.OFFICE_CASH) {
				reason = "Cash payment is not available for your account; please pay by bank transfer";
			}
			else {
				reason = "Online payment is not available for your account; please pay by bank transfer"
						+ (cashAllowed ? " or at our office" : "");
			}
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, reason);
		}
		GatewaySettings gateway = gatewaySettings.current();
		if (method == PaymentMethod.GATEWAY && !gateway.checkoutAllows(order.total())) {
			Money limit = Money.of(gateway.maxAmount(), order.total().currency());
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Online payment is limited to "
					+ limit.display() + " per payment, and this order is " + order.total().display() + ". "
					+ (allowed.contains(PaymentMethod.VIRTUAL_ACCOUNT) ? "Please pay by bank transfer instead."
							: allowed.contains(PaymentMethod.WALLET)
									? "Please pay from your available balance if it covers the order, or cancel it and "
											+ "place orders of up to " + limit.display() + " each."
									: "Please cancel it and place orders of up to " + limit.display() + " each."));
		}
		if (method == PaymentMethod.BANK_TRANSFER && !companyAccounts.anyActive()) {
			// Finance has not added an account to pay into yet (Admin → Company Bank Accounts).
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, cashAllowed
					? "Bank payment is not set up yet. Please pay in cash at our office, or contact support."
					: "Bank payment is not set up yet. Please contact support.");
		}
		if (provider == null && method != PaymentMethod.WALLET) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					humanize(method) + " payments are not available");
		}
		for (Payment inFlight : payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
			// Cash at the office, once chosen, is how the order is paid: the office expects it. To pay another way the
			// investor cancels the order.
			if (inFlight.getMethod() == PaymentMethod.OFFICE_CASH && method != PaymentMethod.OFFICE_CASH) {
				throw new BusinessException(ErrorCode.CONFLICT,
						"You chose to pay in cash at our office. To pay another way, cancel this order and place it again.");
			}
			if (inFlight.getMethod() == method) {
				return Optional.of(view(inFlight, order.orderNumber()));
			}
		}
		return Optional.empty();
	}

	/** How long the order is held for the investor to pay this way, or null to keep its own expiry. */
	private Instant holdUntil(PaymentMethod method, Instant now) {
		return switch (method) {
			case OFFICE_CASH -> now.plus(office.holdWindow());
			case GATEWAY -> now.plus(gatewaySettings.current().checkoutWindow());
			case VIRTUAL_ACCOUNT -> now.plus(gatewaySettings.current().virtualAccountWindow());
			default -> null;
		};
	}

	/** Closes what the provider opened for a payment that was never recorded. Best effort: it also expires by itself. */
	private void closeQuietly(PaymentProvider provider, String reference) {
		try {
			provider.close(reference);
		}
		catch (RuntimeException e) {
			log.warn("Could not close unused {} payment {} at the provider", provider.name(), reference, e);
		}
	}

	/**
	 * What an online investor can reinvest now: their returns and referral commission (null for anyone who cannot pay
	 * from their balance). For display; it is checked again, under the wallet's lock, when spent.
	 */
	@Transactional(readOnly = true)
	public Money reinvestableBalance(UUID userId, Currency currency) {
		return schemes.schemeOf(userId) == ReferralScheme.ONLINE ? reinvestment.available(userId, currency) : null;
	}

	/**
	 * Pays an order from the online investor's available balance, with their password. One transaction: the order is
	 * locked first, as for every payment, then the investor's balances, as a withdrawal locks them; the amount is checked
	 * against the locked balances, the ledger posted and the order confirmed. Nothing is recorded unless all of it
	 * succeeds. Idempotent per key.
	 */
	public PaymentResponse payFromBalance(UUID userId, UUID orderId, String password, String idempotencyKey) {
		String key = IdempotencyKey.require(idempotencyKey);
		if (schemes.schemeOf(userId) != ReferralScheme.ONLINE) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Paying from your balance is available to online investors only");
		}
		users.confirmPassword(userId, password);
		return transactions.execute(status -> {
			PayableOrder order = orders.lockForPayment(orderId);
			Optional<PaymentResponse> answer = validateStart(userId, order, PaymentMethod.WALLET, key);
			if (answer.isPresent()) {
				return answer.get();
			}
			for (Payment inFlight : payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
				// The investor says they already paid another way: that money is verified first, never left to arrive
				// for an order paid twice.
				if (inFlight.hasDepositDetails() || inFlight.getReceiptNumber() != null) {
					throw new BusinessException(ErrorCode.CONFLICT, "You told us you paid this order by "
							+ humanize(inFlight.getMethod()).toLowerCase(Locale.ROOT)
							+ ". Wait for it to be verified, or cancel the order and place it again.");
				}
			}
			Payment payment = payments.saveAndFlush(new Payment(orderId, userId, PaymentMethod.WALLET,
					PaymentMethod.WALLET.name(), "BAL-" + order.orderNumber(), order.total(), key));
			UUID posted = reinvestment.spend(payment, order.orderNumber());
			audit.record(AuditRecord.of(userId, AuditAction.PAYMENT_STARTED, ENTITY, payment.getId())
				.withNewValue(Map.of("orderId", orderId.toString(), "method", PaymentMethod.WALLET,
						"amount", payment.amount().toString(), "ledgerTransactionId", posted.toString())));
			// The evidence is the ledger posting just made, under the wallet's lock: the payment is settled on it.
			settle(payment, order, payment.amount(), posted.toString(), null);
			return view(payment, order.orderNumber());
		});
	}

	/**
	 * The investor tells us how they paid a bank payment: into which company account, and the
	 * transaction id or cheque number. The order then waits for finance to
	 * verify the money instead of lapsing. Details may be corrected until the payment is settled.
	 */
	@Transactional
	public PaymentResponse submitDeposit(UUID userId, UUID paymentId, SubmitDepositRequest request) {
		PaymentKeys payment = keys(paymentId);
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
		if (request.mode() == DepositMode.CASH_DEPOSIT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Cash deposits at the bank are not accepted; pay online or by cheque, or in cash at our office");
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

	/**
	 * The investor gives the receipt number the office handed them for their cash. The order then waits for finance
	 * to confirm the cash instead of lapsing. The number may be corrected until the payment is settled.
	 */
	@Transactional
	public PaymentResponse submitReceipt(UUID userId, UUID paymentId, SubmitReceiptRequest request) {
		PaymentKeys payment = keys(paymentId);
		if (!payment.getUserId().equals(userId)) {
			throw new ResourceNotFoundException("Payment", paymentId);
		}
		if (payment.getMethod() != PaymentMethod.OFFICE_CASH) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only cash payments at the office take a receipt number");
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
		String receiptNumber = request.receiptNumber().strip().toUpperCase(Locale.ROOT);
		locked.submitReceipt(receiptNumber, now);
		payments.flush();
		order = orders.holdForVerification(order.id(), now.plus(bankTransfer.verificationWindow()));
		audit.record(AuditRecord.of(userId, AuditAction.PAYMENT_DETAILS_SUBMITTED, ENTITY, paymentId)
			.withNewValue(Map.of("receiptNumber", receiptNumber, "orderExpiresAt", order.expiresAt().toString())));
		return view(locked, order.orderNumber());
	}

	/**
	 * A gateway payment of the investor's own, still awaiting the money; the checkout's result is checked against it.
	 */
	@Transactional(readOnly = true)
	public Payment ownPendingCheckout(UUID userId, UUID paymentId) {
		Payment payment = load(paymentId);
		if (!payment.getUserId().equals(userId)) {
			throw new ResourceNotFoundException("Payment", paymentId);
		}
		if (payment.getMethod() != PaymentMethod.GATEWAY) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only online payments are paid at the checkout");
		}
		return payment;
	}

	/**
	 * The gateway took money: {@code gatewayPaymentId} for {@code received}, against the payment it knows as
	 * {@code providerReference} (a checkout order or a virtual account). Confirms the order when it can; otherwise the
	 * money is flagged for refund, never silently kept. Recording the same gateway payment again (a repeated webhook, or
	 * the webhook and the checkout both reporting it) changes nothing. More money into a payment already settled (a
	 * second transfer into the same virtual account) is recorded as a payment of its own: it pays the order if the order
	 * is still waiting, otherwise it is refunded.
	 *
	 * @return the payment that records the money, or empty if the reference is not one of ours (the money is then
	 *         recorded as an unmatched receipt for finance)
	 */
	@Transactional
	public Optional<PaymentResponse> recordGatewayPayment(String provider, String providerReference,
			String gatewayPaymentId, Money received) {
		// Only the ids: the payment itself is read once locked, after its order.
		Optional<PaymentKeys> found = payments.findKeysByProviderReference(provider, providerReference);
		if (found.isEmpty()) {
			unmatched.record(provider, providerReference, gatewayPaymentId, received);
			return Optional.empty();
		}
		PayableOrder order = orders.lockForPayment(found.get().getOrderId());
		Payment locked = lockPayment(found.get().getId());
		Optional<Payment> recorded = payments.findByProviderAndExternalReference(provider, gatewayPaymentId);
		if (recorded.isPresent()) {
			return Optional.of(view(recorded.get(), order.orderNumber()));
		}
		if (!locked.getStatus().isSettled()) {
			settle(locked, order, received, gatewayPaymentId, null);
			if (locked.getStatus() == PaymentStatus.SUCCEEDED) {
				closeAtProviderAfterCommit(locked);
			}
			return Optional.of(view(locked, order.orderNumber()));
		}
		if (order.status() == OrderStatus.PENDING_PAYMENT) {
			for (Payment pending : payments.findByOrderIdAndStatus(order.id(), PaymentStatus.PENDING)) {
				cancelPending(pending, "Order paid by another payment");
			}
			payments.flush();
		}
		Payment extra = payments.saveAndFlush(new Payment(order.id(), locked.getUserId(), locked.getMethod(), provider,
				gatewayPaymentId, received, "gateway:" + gatewayPaymentId));
		settle(extra, order, received, gatewayPaymentId, null);
		if (extra.getStatus() == PaymentStatus.SUCCEEDED) {
			closeAtProviderAfterCommit(locked); // The virtual account the money came into, not the new record.
		}
		return Optional.of(view(extra, order.orderNumber()));
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
		return found.stream().map(p -> {
			PaymentResponse view = view(p, order.orderNumber(), accounts, active);
			return ownerId == null ? view.withAssignee(assignee(p)) : view;
		}).toList();
	}

	// -------------------------------------------------------------------------------- staff

	/**
	 * Finance records that a bank transfer arrived, or that cash was received at the office ({@code externalReference}
	 * is then the receipt number, which no other payment of that method may have). Nobody may confirm a payment for their
	 * own order.
	 */
	@Transactional
	public PaymentResponse confirmBankTransfer(UUID actorId, UUID paymentId, ConfirmTransferRequest request) {
		PaymentKeys payment = keys(paymentId);
		if (!isManual(payment.getMethod())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Only bank and office payments are confirmed manually");
		}
		if (payment.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot confirm a payment for your own order");
		}
		PayableOrder order = orders.lockForPayment(payment.getOrderId());
		Payment locked = lockPayment(paymentId);
		requireTaskOf(actorId, locked);
		if (locked.getStatus() != PaymentStatus.PENDING && locked.getStatus() != PaymentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + locked.getStatus());
		}
		// A cancelled payment is confirmed only for money that arrived late; not while the order's live payment (the one
		// that replaced it) is still pending.
		if (locked.getStatus() == PaymentStatus.CANCELLED) {
			payments.findByOrderIdAndStatus(locked.getOrderId(), PaymentStatus.PENDING).stream().findFirst()
				.ifPresent(pending -> {
					throw new BusinessException(ErrorCode.CONFLICT, "This payment was cancelled and " + pending.getProviderReference()
							+ " is pending for the order: confirm that one instead");
				});
		}
		Money received = Money.of(request.amountReceived(), locked.amount().currency());
		if (!received.equals(locked.amount())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The amount due is "
					+ locked.amount().display() + "; partial or excess transfers must be returned to the investor");
		}
		// One transfer or receipt is evidence for one payment: a reference already recorded is refused.
		String reference = request.externalReference().strip();
		payments.findOthersWithExternalReference(locked.getMethod(), reference, locked.getId()).stream().findFirst()
			.ifPresent(other -> {
				throw new BusinessException(ErrorCode.CONFLICT, "Reference " + reference + " is already recorded for payment "
						+ other + ": check the bank statement or receipt book");
			});
		settle(locked, order, received, reference, actorId);
		return staffView(locked, order.orderNumber());
	}

	/**
	 * Finance could not find the money (or the cheque bounced). The order keeps its reservation until
	 * it expires, so the investor can pay again or correct the details in a new payment.
	 */
	@Transactional
	public PaymentResponse rejectBankPayment(UUID actorId, UUID paymentId, String reason) {
		PaymentKeys payment = keys(paymentId);
		if (!isManual(payment.getMethod())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only bank and office payments are rejected manually");
		}
		if (payment.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide on a payment for your own order");
		}
		PayableOrder order = orders.lockForPayment(payment.getOrderId());
		Payment locked = lockPayment(paymentId);
		if (locked.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + humanize(locked.getStatus()));
		}
		requireTaskOf(actorId, locked);
		String note = reason.strip();
		locked.fail(note);
		payments.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_REJECTED, ENTITY, paymentId)
			.withNewValue(Map.of("reason", note)));
		outbox.publish(DomainEvent.of(KafkaTopics.PAYMENT_FAILED, "PaymentFailed", ENTITY, paymentId,
				Map.of("paymentId", paymentId.toString(), "orderId", order.id().toString(),
						"userId", locked.getUserId().toString(), "reason", note)));
		return staffView(locked, order.orderNumber());
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
		depositLedger.refunded(payment, actorId);
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
		return page.map(p -> view(p, numbers.get(p.getOrderId()), accounts, active).withAssignee(assignee(p)));
	}

	/**
	 * A finance member takes the task of confirming or rejecting a bank or office payment: from now on only they may
	 * decide it. Taking one already assigned to you changes nothing; one another member holds must be given back first.
	 */
	@Transactional
	public PaymentResponse takeTask(UUID actorId, UUID paymentId) {
		Payment payment = lockPayment(paymentId);
		if (!isManual(payment.getMethod())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only bank and office payments are confirmed manually");
		}
		if (payment.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide on a payment for your own order");
		}
		if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + humanize(payment.getStatus()));
		}
		if (!actorId.equals(payment.getTaskAssigneeId())) {
			if (payment.getTaskAssigneeId() != null) {
				throw new BusinessException(ErrorCode.CONFLICT, "Another finance member is already confirming this payment");
			}
			payment.takeTask(actorId, clock.instant());
			payments.flush();
			audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_TASK_ASSIGNED, ENTITY, paymentId));
		}
		return staffView(payment, orders.payable(payment.getOrderId()).orderNumber());
	}

	/** The member holding the task gives it back to the queue, undecided, for another finance member to take. */
	@Transactional
	public PaymentResponse releaseTask(UUID actorId, UUID paymentId) {
		Payment payment = lockPayment(paymentId);
		if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This payment is already " + humanize(payment.getStatus()));
		}
		requireTaskOf(actorId, payment);
		payment.releaseTask();
		payments.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_TASK_UNASSIGNED, ENTITY, paymentId));
		return staffView(payment, orders.payable(payment.getOrderId()).orderNumber());
	}

	private static void requireTaskOf(UUID actorId, Payment payment) {
		if (!actorId.equals(payment.getTaskAssigneeId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, payment.getTaskAssigneeId() == null
					? "Assign this payment to yourself before confirming or rejecting it"
					: "Another finance member is confirming this payment");
		}
	}

	private PaymentResponse staffView(Payment payment, String orderNumber) {
		return view(payment, orderNumber).withAssignee(assignee(payment));
	}

	private Assignee assignee(Payment payment) {
		if (payment.getTaskAssigneeId() == null) {
			return null;
		}
		UserAccount member = users.getAccount(payment.getTaskAssigneeId());
		return new Assignee(member.id(), (member.firstName() + " " + member.lastName()).strip(), payment.getTaskAssignedAt());
	}

	// ----------------------------------------------------------------------------- internal

	/** A closed order takes no more money: cancel attempts still in flight (same transaction). */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onOrderClosed(OrderClosedEvent event) {
		for (Payment payment : payments.findByOrderIdAndStatus(event.orderId(), PaymentStatus.PENDING)) {
			cancelPending(payment, "Order " + event.status().name().toLowerCase(Locale.ROOT));
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
					cancelPending(other, "Order paid by another payment");
				}
			}
		}
		Instant now = clock.instant();
		payment.settle(accepted, externalReference, actorId, note, now);
		payments.flush();
		if (payment.getMethod() != PaymentMethod.WALLET) {
			// A balance payment is posted as a reinvestment by payFromBalance; no money arrived.
			depositLedger.received(payment, received, accepted, actorId);
		}
		if (accepted && !orders.confirmPaid(order.id(), payment.getId(), payment.getMethod() == PaymentMethod.OFFICE_CASH)) {
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

	/** Cancels a payment still in flight; a virtual account stops taking money once this commits. */
	private void cancelPending(Payment payment, String reason) {
		payment.cancel(reason);
		closeAtProviderAfterCommit(payment);
	}

	/** Once this transaction commits, the provider stops taking money for the payment (a virtual account is closed). */
	private void closeAtProviderAfterCommit(Payment payment) {
		if (payment.getMethod() != PaymentMethod.VIRTUAL_ACCOUNT || !TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		PaymentProvider provider = providersByMethod.get(payment.getMethod());
		String reference = payment.getProviderReference();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				provider.close(reference);
			}
		});
	}

	private PaymentResponse view(Payment payment, String orderNumber) {
		Map<UUID, CompanyBankAccount> accounts = companyAccounts.byId(depositAccountIds(List.of(payment)));
		return view(payment, orderNumber, accounts, companyAccounts.active());
	}

	private PaymentResponse view(Payment payment, String orderNumber, Map<UUID, CompanyBankAccount> accounts,
			List<CompanyBankAccountResponse> activeAccounts) {
		OfficeCashInstructions officeCash = payment.getMethod() == PaymentMethod.OFFICE_CASH
				&& payment.getStatus() == PaymentStatus.PENDING
				? new OfficeCashInstructions(payment.getProviderReference(), MoneyResponse.from(payment.amount()),
						office.address(), office.hours(), payment.getCreatedAt().plus(office.holdWindow()))
				: null;
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
		boolean pending = payment.getStatus() == PaymentStatus.PENDING;
		CheckoutInstructions checkout = null;
		if (pending && payment.getMethod() == PaymentMethod.GATEWAY) {
			UserAccount investor = users.getAccount(payment.getUserId());
			RazorpayKeys keys = razorpay.current();
			checkout = new CheckoutInstructions(keys.keyId(), payment.getProviderReference(),
					Paise.of(payment.amount()), payment.amount().currency().getCurrencyCode(), keys.merchantName(),
					"Order " + orderNumber, (investor.firstName() + " " + investor.lastName()).strip(), investor.email());
		}
		VirtualAccountInstructions virtualAccount = null;
		if (pending && payment.virtualAccount() != null) {
			PaymentProvider.VirtualAccountDetails va = payment.virtualAccount();
			virtualAccount = new VirtualAccountInstructions(MoneyResponse.from(payment.amount()), va.accountNumber(),
					va.ifsc(), va.accountName(), va.bankName(), va.upiId(),
					payment.getCreatedAt().plus(gatewaySettings.current().virtualAccountWindow()));
		}
		return PaymentResponse.from(payment, orderNumber, instructions, deposit, officeCash, checkout, virtualAccount);
	}

	private static List<UUID> depositAccountIds(List<Payment> found) {
		return found.stream().map(Payment::getCompanyBankAccountId).filter(Objects::nonNull).distinct().toList();
	}

	private static boolean isGateway(PaymentMethod method) {
		return method == PaymentMethod.GATEWAY || method == PaymentMethod.VIRTUAL_ACCOUNT;
	}

	/** Payments finance confirms or rejects by hand: bank transfers and cash at the office. */
	private static boolean isManual(PaymentMethod method) {
		return method == PaymentMethod.BANK_TRANSFER || method == PaymentMethod.OFFICE_CASH;
	}

	/** The payment's fixed facts, without loading it: it is loaded once locked (see {@link PaymentKeys}). */
	private PaymentKeys keys(UUID paymentId) {
		return payments.findKeysById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private Payment load(UUID paymentId) {
		return payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private Payment lockPayment(UUID paymentId) {
		return payments.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
	}

	private static String humanize(PaymentMethod method) {
		return switch (method) {
			case CARD -> "Card";
			case BANK_TRANSFER -> "Bank";
			case OFFICE_CASH -> "Cash";
			case GATEWAY -> "Online";
			case VIRTUAL_ACCOUNT -> "Bank transfer";
			case WALLET -> "Available balance";
		};
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
			if (c.q() != null && !c.q().isBlank()) {
				String q = c.q().strip().toUpperCase(Locale.ROOT);
				List<Predicate> any = new ArrayList<>(List.of(cb.equal(cb.upper(root.get("externalReference")), q),
						cb.equal(cb.upper(root.get("depositReference")), q), cb.equal(cb.upper(root.get("providerReference")), q)));
				if (c.qOrderId() != null) {
					any.add(cb.equal(root.get("orderId"), c.qOrderId()));
				}
				if (c.qInvestorId() != null) {
					any.add(cb.equal(root.get("userId"), c.qInvestorId()));
				}
				predicates.add(cb.or(any.toArray(Predicate[]::new)));
			}
			else if (c.searchOnly()) {
				return cb.disjunction();
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
