package com.sealease.backend.agreement.service;

import com.sealease.backend.agreement.dto.AgreementPdf;
import com.sealease.backend.agreement.dto.AgreementPreview;
import com.sealease.backend.agreement.dto.AgreementTemplateRequest;
import com.sealease.backend.agreement.dto.AgreementTemplateView;
import com.sealease.backend.agreement.entity.AgreementTemplate;
import com.sealease.backend.agreement.repository.AgreementTemplateRepository;
import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The lease agreement's wording, which admins maintain (AGREEMENT_TEMPLATE_MANAGE). Each save is a new version, used
 * for every agreement issued from then on; agreements already issued keep the wording they were issued with.
 */
@Service
public class AgreementTemplateService {

	private static final String ENTITY = "AGREEMENT_TEMPLATE";

	private final AgreementTemplateRepository templates;
	private final AgreementRenderer renderer;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final Clock clock;

	public AgreementTemplateService(AgreementTemplateRepository templates, AgreementRenderer renderer,
			UserAccountService accounts, AuditService audit, Clock clock) {
		this.templates = templates;
		this.renderer = renderer;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AgreementTemplateView view() {
		List<AgreementTemplate> latest = templates.findTop20ByOrderByVersionDesc();
		Map<UUID, UserAccount> authors = accounts.getAccounts(latest.stream().map(AgreementTemplate::getCreatedBy)
			.filter(Objects::nonNull).distinct().toList());
		List<AgreementTemplateView.Version> history = latest.stream().map(t -> version(t, authors, false)).toList();
		return new AgreementTemplateView(latest.isEmpty() ? null : version(latest.getFirst(), authors, true),
				Arrays.stream(Placeholder.values())
					.map(p -> new AgreementTemplateView.PlaceholderInfo(p.key(), p.description(), p.example()))
					.toList(),
				history);
	}

	/** Saves new wording as the next version, in force from now on. Unknown placeholders are refused. */
	@Transactional
	public AgreementTemplateView.Version save(UUID actorId, AgreementTemplateRequest request) {
		String title = request.title().strip();
		String body = normalise(request.body());
		Placeholder.requireKnown(title);
		Placeholder.requireKnown(body);
		int next = templates.findTopByOrderByVersionDesc().map(AgreementTemplate::getVersion).orElse(0) + 1;
		AgreementTemplate saved = templates.saveAndFlush(new AgreementTemplate(next, title, body, actorId, clock.instant()));
		audit.record(AuditRecord.of(actorId, AuditAction.AGREEMENT_TEMPLATE_UPDATED, ENTITY, saved.getId())
			.withNewValue(Map.of("version", next, "title", title)));
		return version(saved, accounts.getAccounts(List.of(actorId)), true);
	}

	/** The wording filled in with example values; nothing is saved. */
	public AgreementPreview preview(AgreementTemplateRequest request) {
		Placeholder.requireKnown(request.title());
		Placeholder.requireKnown(request.body());
		Map<Placeholder, String> examples = Placeholder.examples();
		return new AgreementPreview(Placeholder.fill(request.title().strip(), examples),
				Placeholder.fill(normalise(request.body()), examples));
	}

	/** The wording in force as a PDF, filled in with example values. */
	@Transactional(readOnly = true)
	public AgreementPdf samplePdf() {
		AgreementTemplate t = current();
		Map<Placeholder, String> examples = Placeholder.examples();
		return new AgreementPdf("agreement-sample-v" + t.getVersion(), renderer.pdf(Placeholder.fill(t.getTitle(), examples),
				"SAMPLE · " + examples.get(Placeholder.AGREEMENT_NUMBER) + " · " + examples.get(Placeholder.AGREEMENT_DATE),
				Placeholder.fill(t.getBody(), examples), "Sample filled in with example values · wording version " + t.getVersion()));
	}

	/** The version in force. */
	@Transactional(readOnly = true)
	public AgreementTemplate current() {
		return templates.findTopByOrderByVersionDesc()
			.orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"No lease agreement wording has been written yet"));
	}

	/** Windows line breaks become plain ones, and trailing spaces go, so stored wording is consistent. */
	private static String normalise(String body) {
		return body.replace("\r\n", "\n").lines().map(String::stripTrailing)
			.collect(java.util.stream.Collectors.joining("\n")).strip();
	}

	private static AgreementTemplateView.Version version(AgreementTemplate t, Map<UUID, UserAccount> authors, boolean withBody) {
		UserAccount author = t.getCreatedBy() == null ? null : authors.get(t.getCreatedBy());
		return new AgreementTemplateView.Version(t.getId(), t.getVersion(), t.getTitle(), withBody ? t.getBody() : null,
				t.getCreatedAt(), author == null ? null : (author.firstName() + " " + author.lastName()).strip());
	}

}
