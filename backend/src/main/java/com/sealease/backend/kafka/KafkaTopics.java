package com.sealease.backend.kafka;

import java.util.List;

/**
 * Catalogue of domain event topics. Topic names are part of the inter-module (and future
 * inter-service) contract, so they live in code where {@code @KafkaListener} can reference them;
 * partitions, replication and retry behaviour are configuration ({@link PlatformKafkaProperties}).
 *
 * <p>Every topic has a dead-letter companion named {@code <topic>}{@link #DLT_SUFFIX}.
 */
public final class KafkaTopics {

	public static final String DLT_SUFFIX = ".dlt";

	public static final String INVESTMENT_CREATED = "investment.created";
	public static final String INVESTMENT_CONFIRMED = "investment.confirmed";
	public static final String INVESTMENT_COMPLETED = "investment.completed";

	public static final String PAYMENT_SUCCESS = "payment.success";
	public static final String PAYMENT_FAILED = "payment.failed";

	public static final String RENTAL_GENERATED = "rental.generated";
	public static final String EARNING_CREATED = "earning.created";
	public static final String REFERRAL_EARNING_CREATED = "referral.earning.created";

	public static final String WITHDRAWAL_REQUESTED = "withdrawal.requested";
	public static final String WITHDRAWAL_APPROVED = "withdrawal.approved";
	public static final String WITHDRAWAL_BATCHED = "withdrawal.batched";
	public static final String WITHDRAWAL_PROCESSING = "withdrawal.processing";
	public static final String WITHDRAWAL_COMPLETED = "withdrawal.completed";
	public static final String WITHDRAWAL_FAILED = "withdrawal.failed";

	public static final String NOTIFICATION_CREATED = "notification.created";
	public static final String INVOICE_GENERATED = "invoice.generated";

	public static final String LEAD_CREATED = "lead.created";
	public static final String LEAD_UPDATED = "lead.updated";

	public static final List<String> ALL = List.of(
			INVESTMENT_CREATED, INVESTMENT_CONFIRMED, INVESTMENT_COMPLETED,
			PAYMENT_SUCCESS, PAYMENT_FAILED,
			RENTAL_GENERATED, EARNING_CREATED, REFERRAL_EARNING_CREATED,
			WITHDRAWAL_REQUESTED, WITHDRAWAL_APPROVED, WITHDRAWAL_BATCHED, WITHDRAWAL_PROCESSING,
			WITHDRAWAL_COMPLETED, WITHDRAWAL_FAILED,
			NOTIFICATION_CREATED, INVOICE_GENERATED,
			LEAD_CREATED, LEAD_UPDATED);

	private KafkaTopics() {
	}

	public static String deadLetterOf(String topic) {
		return topic + DLT_SUFFIX;
	}

}
