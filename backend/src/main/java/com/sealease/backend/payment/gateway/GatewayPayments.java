package com.sealease.backend.payment.gateway;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.VerifyCheckoutRequest;
import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.gateway.RazorpayClient.GatewayPayment;
import com.sealease.backend.payment.gateway.RazorpayClient.RazorpayException;
import com.sealease.backend.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Money Razorpay reports, from the checkout (the investor's browser hands back a signed result) and from webhooks
 * (Razorpay calls us; the delivery is signed). Either way nothing is taken on trust: a checkout result is checked with
 * Razorpay's API, a webhook by its signature. The payment service then records the money; reporting it twice changes
 * nothing. Calls to Razorpay happen outside any database transaction.
 */
@Service
public class GatewayPayments {

	private static final Logger log = LoggerFactory.getLogger(GatewayPayments.class);
	private static final String PROVIDER = RazorpayCheckoutProvider.NAME;

	private final PaymentService payments;
	private final RazorpayClient razorpay;
	private final RazorpayCredentials credentials;
	private final JsonMapper json;

	public GatewayPayments(PaymentService payments, RazorpayClient razorpay, RazorpayCredentials credentials,
			JsonMapper json) {
		this.payments = payments;
		this.razorpay = razorpay;
		this.credentials = credentials;
		this.json = json;
	}

	/** The investor finished the checkout: check its signature and the payment with Razorpay, then record it. */
	public PaymentResponse verifyCheckout(UUID userId, UUID paymentId, VerifyCheckoutRequest request) {
		Payment payment = payments.ownPendingCheckout(userId, paymentId);
		if (!payment.getProviderReference().equals(request.gatewayOrderId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This checkout is for another payment");
		}
		if (!RazorpaySignatures.checkoutValid(credentials.current().keySecret(), request.gatewayOrderId(),
				request.gatewayPaymentId(), request.signature())) {
			log.warn("Checkout result for payment {} has a bad signature", paymentId);
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "The payment could not be verified");
		}
		GatewayPayment taken = taken(request.gatewayPaymentId());
		if (!request.gatewayOrderId().equals(taken.orderId())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This checkout is for another payment");
		}
		return payments.recordGatewayPayment(PROVIDER, taken.orderId(), taken.id(),
				Paise.toMoney(taken.amountPaise(), taken.currency()))
			.orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " vanished"));
	}

	/**
	 * A webhook delivery. Checkout payments are recorded on payment.captured (an authorized one is captured first);
	 * transfers into a virtual account on virtual_account.credited. Other events are acknowledged and ignored.
	 *
	 * @throws BusinessException UNAUTHORIZED for a delivery that is not signed with the webhook secret
	 */
	public void onWebhook(String body, String signature) {
		if (!RazorpaySignatures.webhookValid(credentials.current().webhookSecret(), body, signature)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid webhook signature");
		}
		JsonNode event = json.readTree(body);
		String type = event.path("event").asString();
		JsonNode payment = event.path("payload").path("payment").path("entity");
		switch (type) {
			case "payment.authorized", "payment.captured", "order.paid" -> {
				String orderId = text(payment, "order_id");
				if (orderId == null) {
					return; // A transfer into a virtual account: recorded on virtual_account.credited.
				}
				GatewayPayment taken = "captured".equals(text(payment, "status")) ? new GatewayPayment(
						payment.path("id").asString(), orderId, payment.path("amount").asLong(),
						payment.path("currency").asString(), "captured") : taken(payment.path("id").asString());
				payments.recordGatewayPayment(PROVIDER, orderId, taken.id(),
						Paise.toMoney(taken.amountPaise(), taken.currency()));
			}
			case "virtual_account.credited" -> {
				String account = text(event.path("payload").path("virtual_account").path("entity"), "id");
				if (account == null) {
					log.warn("virtual_account.credited without a virtual account: {}", payment.path("id").asString());
					return;
				}
				payments.recordGatewayPayment(PROVIDER, account, payment.path("id").asString(),
						Paise.toMoney(payment.path("amount").asLong(), payment.path("currency").asString()));
			}
			default -> log.debug("Ignoring Razorpay event {}", type);
		}
	}

	/** The payment as Razorpay has it, captured (an authorized payment is captured now). */
	private GatewayPayment taken(String gatewayPaymentId) {
		try {
			GatewayPayment payment = razorpay.fetchPayment(gatewayPaymentId);
			if (payment.authorized()) {
				payment = razorpay.capture(payment.id(), payment.amountPaise(), payment.currency());
			}
			if (!payment.captured()) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"The payment has not gone through (" + payment.status() + "); please try again");
			}
			return payment;
		}
		catch (RazorpayException e) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, e.getMessage());
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isNull() || value.isMissingNode() ? null : value.asString();
	}

}
