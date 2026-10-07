package com.sealease.backend.crm.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.crm.dto.PublicLeadRequest;
import com.sealease.backend.crm.entity.ActivityType;
import com.sealease.backend.crm.entity.Lead;
import com.sealease.backend.crm.entity.LeadSource;
import com.sealease.backend.crm.repository.LeadRepository;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.user.service.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The public interest form. Callers always get the same answer, so the form reveals nothing about
 * which emails are known; bots that fill the honeypot are dropped silently.
 */
@Service
public class PublicLeadService {

	private static final Logger log = LoggerFactory.getLogger(PublicLeadService.class);

	private final LeadRepository leads;
	private final LeadService leadService;
	private final AuditService audit;
	private final FixedWindowRateLimiter perIp;
	private final Clock clock;

	public PublicLeadService(LeadRepository leads, LeadService leadService, AuditService audit, CrmProperties properties,
			Clock clock) {
		this.leads = leads;
		this.leadService = leadService;
		this.audit = audit;
		this.perIp = new FixedWindowRateLimiter(properties.publicFormPerIp().requests(),
				properties.publicFormPerIp().window(), clock);
		this.clock = clock;
	}

	@Transactional
	public void submit(PublicLeadRequest request, ClientInfo client) {
		perIp.acquire(client.ipAddress() == null ? "unknown" : client.ipAddress());
		if (request.website() != null && !request.website().isBlank()) {
			log.info("Dropped an interest-form submission that filled the honeypot");
			return;
		}
		Instant now = clock.instant();
		String email = EmailNormalizer.normalize(request.email());
		String message = request.message() == null || request.message().isBlank() ? null : request.message().strip();

		Optional<Lead> open = leads.lockOpenByEmail(email);
		if (open.isPresent()) {
			leadService.log(open.get(), ActivityType.SYSTEM,
					"Submitted the website form again" + (message == null ? "" : ": " + message), null);
			return;
		}
		Lead lead = new Lead("LD-" + String.format("%06d", leads.nextNumber()), LeadSource.WEBSITE,
				new Lead.Contact(request.firstName().strip(), blank(request.lastName()), email, blank(request.phone()),
						blank(request.country()), request.interest(), null),
				message, null, now);
		leadService.linkExistingAccount(lead);
		leads.saveAndFlush(lead);
		leadService.log(lead, ActivityType.SYSTEM, "Enquiry received from the website", null);
		audit.record(AuditRecord.of(null, AuditAction.LEAD_CREATED, "LEAD", lead.getId())
			.withNewValue(Map.of("reference", lead.getReference(), "source", LeadSource.WEBSITE)));
		leadService.publish(KafkaTopics.LEAD_CREATED, "LeadCreated", lead);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
