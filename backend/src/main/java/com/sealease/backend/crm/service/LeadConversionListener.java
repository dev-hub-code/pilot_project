package com.sealease.backend.crm.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.crm.entity.ActivityType;
import com.sealease.backend.crm.entity.Lead;
import com.sealease.backend.crm.repository.LeadRepository;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.order.event.OrderConfirmedEvent;
import com.sealease.backend.user.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;

/**
 * Follows leads into the platform: registering with a lead's email links the lead to the account,
 * and the account's first confirmed investment wins it.
 *
 * <p>Both run <em>after</em> the registration or payment has committed, in their own transaction,
 * and swallow their own failures: the CRM can never undo or fail a sign-up or a payment.
 */
@Component
public class LeadConversionListener {

	private static final Logger log = LoggerFactory.getLogger(LeadConversionListener.class);

	private final LeadRepository leads;
	private final LeadService leadService;
	private final AuditService audit;
	private final TransactionTemplate newTransaction;
	private final Clock clock;

	public LeadConversionListener(LeadRepository leads, LeadService leadService, AuditService audit,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.leads = leads;
		this.leadService = leadService;
		this.audit = audit;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@TransactionalEventListener
	public void onRegistered(UserRegisteredEvent event) {
		safely("link a lead to new account " + event.userId(), () -> leads.lockOpenByEmail(event.email())
			.filter(lead -> lead.getUserId() == null)
			.ifPresent(lead -> {
				lead.linkAccount(event.userId());
				leadService.log(lead, ActivityType.SYSTEM, "Registered an investor account", null);
				leadService.publish(KafkaTopics.LEAD_UPDATED, "LeadRegistered", lead);
			}));
	}

	@TransactionalEventListener
	public void onOrderConfirmed(OrderConfirmedEvent event) {
		safely("win the lead of account " + event.userId(), () -> {
			for (Lead lead : leads.lockOpenByUserId(event.userId())) {
				lead.win(event.total(), event.orderId(), clock.instant());
				leadService.log(lead, ActivityType.SYSTEM,
						"Won: first investment confirmed (" + event.orderNumber() + ", " + event.total().display() + ")", null);
				audit.record(AuditRecord.of(null, AuditAction.LEAD_WON, "LEAD", lead.getId())
					.withNewValue(Map.of("orderId", event.orderId().toString(), "amount", event.total().toString())));
				leadService.publish(KafkaTopics.LEAD_UPDATED, "LeadWon", lead);
			}
		});
	}

	private void safely(String what, Runnable work) {
		try {
			newTransaction.executeWithoutResult(status -> work.run());
		}
		catch (RuntimeException ex) {
			log.warn("CRM could not {}; the lead can be updated by hand", what, ex);
		}
	}

}
