package com.sealease.backend.kyc.service;

import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.service.SupportService;
import com.sealease.backend.kyc.entity.KycFollowUp;
import com.sealease.backend.kyc.repository.KycFollowUpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Investors who have not verified their identity a day after registering get one support ticket (opened by the
 * system, so support can follow up and they can reply). It is resolved once they submit their documents.
 */
@Service
public class KycFollowUpService {

	private static final Logger log = LoggerFactory.getLogger(KycFollowUpService.class);
	private static final int BATCH = 200;

	private final KycFollowUpRepository followUps;
	private final SupportService support;
	private final KycFollowUpProperties properties;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public KycFollowUpService(KycFollowUpRepository followUps, SupportService support, KycFollowUpProperties properties,
			TransactionTemplate transactions, Clock clock) {
		this.followUps = followUps;
		this.support = support;
		this.properties = properties;
		this.transactions = transactions;
		this.clock = clock;
	}

	/**
	 * Opens a ticket for every investor now due one, each in its own transaction.
	 *
	 * @return how many tickets were opened
	 */
	public int openDue() {
		int opened = 0;
		while (true) {
			var due = followUps.findDue(clock.instant().minus(properties.after()), BATCH);
			int before = opened;
			for (UUID userId : due) {
				try {
					if (Boolean.TRUE.equals(transactions.execute(status -> open(userId)))) {
						opened++;
					}
				}
				catch (DataIntegrityViolationException ex) {
					// Another instance opened it first.
				}
				catch (RuntimeException ex) {
					log.warn("Could not open the identity verification ticket for user {}", userId, ex);
				}
			}
			if (due.size() < BATCH || opened == before) {
				return opened;
			}
		}
	}

	private boolean open(UUID userId) {
		if (followUps.existsById(userId)) {
			return false;
		}
		Instant now = clock.instant();
		UUID ticketId = support.openOnBehalf(null, userId, TicketCategory.ACCOUNT, null, null, null,
				"Verify your identity to start investing",
				"Welcome to SeaLease! You have not verified your identity yet. To invest, earn rent and withdraw, upload"
						+ " your Aadhaar card and PAN card under Profile → Identity verification. If anything is stopping you,"
						+ " reply here and we will help.",
				TicketPriority.NORMAL, null);
		followUps.saveAndFlush(new KycFollowUp(userId, ticketId, now));
		return true;
	}

	/** The investor submitted their documents: resolves their follow-up ticket, if they have one. Caller's transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void documentsSubmitted(UUID userId) {
		followUps.findById(userId).ifPresent(f -> support.resolveWithNote(null, f.getTicketId(),
				"Thank you, we have your identity documents and are reviewing them. We will let you know once they are verified."));
	}

}
