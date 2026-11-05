package com.sealease.backend.agreement.service;

import com.sealease.backend.agreement.dto.AgreementPdf;
import com.sealease.backend.agreement.dto.LeaseAgreementDocument;
import com.sealease.backend.agreement.dto.LeaseAgreementResponse;
import com.sealease.backend.agreement.entity.AgreementAcceptance;
import com.sealease.backend.agreement.entity.AgreementTemplate;
import com.sealease.backend.agreement.entity.LeaseAgreement;
import com.sealease.backend.agreement.repository.AgreementAcceptanceRepository;
import com.sealease.backend.agreement.repository.AgreementTemplateRepository;
import com.sealease.backend.agreement.repository.LeaseAgreementRepository;
import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.invoice.service.InvoiceProperties;
import com.sealease.backend.kyc.service.KycService;
import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Issues each holding's investment agreement when its order is confirmed: the wording in force, filled in with the
 * investment's facts, numbered ({@code AGR-2026-000042}) and stored word for word. The investor then accepts it, which
 * starts the lease. Until then the lease dates are not known, so the stored wording keeps their placeholders and they
 * read "the day this Agreement is accepted"; once accepted, the dates of the lease it started are shown. Until the
 * investor accepts, the agreement is shown in the wording now in force (its number and issue date unchanged), so a
 * change to the wording reaches agreements still waiting; the wording accepted is recorded with the acceptance and
 * stays as it was. Agreements and acceptances are immutable; the PDF is rendered from them whenever it is downloaded.
 */
@Service
public class LeaseAgreementService {

	private static final String ENTITY = "LEASE_AGREEMENT";
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm z", Locale.ENGLISH);

	private final LeaseAgreementRepository agreements;
	private final AgreementAcceptanceRepository acceptances;
	private final AgreementTemplateRepository templates;
	private final AgreementTemplateService wording;
	private final AgreementRenderer renderer;
	private final UserProfileService profiles;
	private final InvoiceProperties company;
	private final DisplayTimeZone display;
	private final AuditService audit;
	private final ObjectProvider<AgreementFactsSource> factsSource;
	private final ObjectProvider<KycService> kyc;
	private final Clock clock;

	public LeaseAgreementService(LeaseAgreementRepository agreements, AgreementAcceptanceRepository acceptances,
			AgreementTemplateRepository templates, AgreementTemplateService wording, AgreementRenderer renderer,
			UserProfileService profiles, InvoiceProperties company, DisplayTimeZone display, AuditService audit,
			ObjectProvider<AgreementFactsSource> factsSource, ObjectProvider<KycService> kyc, Clock clock) {
		this.agreements = agreements;
		this.acceptances = acceptances;
		this.templates = templates;
		this.wording = wording;
		this.renderer = renderer;
		this.profiles = profiles;
		this.company = company;
		this.display = display;
		this.audit = audit;
		// Looked up when needed: the source (orders) itself depends on this service to issue agreements.
		this.factsSource = factsSource;
		// Looked up when needed, like the facts source, so the KYC module's own dependencies stay out of this one.
		this.kyc = kyc;
		this.clock = clock;
	}

	/**
	 * Called in the transaction that issues the agreement. A holding has one agreement: if it already has one, that
	 * one is returned unchanged. When the facts have no lease dates, the investor must accept the agreement before the
	 * lease starts.
	 *
	 * @param actorId who issued the agreement
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public LeaseAgreementResponse issue(UUID actorId, AgreementFacts facts) {
		var existing = agreements.findByHoldingId(facts.holdingId());
		if (existing.isPresent()) {
			return response(existing.get());
		}
		Instant now = clock.instant();
		AgreementTemplate template = wording.current();
		String number = "AGR-%d-%06d".formatted(now.atZone(ZoneOffset.UTC).getYear(), agreements.nextNumber());
		Map<Placeholder, String> values = values(facts, number, LocalDate.ofInstant(now, display.zone()));
		values.put(Placeholder.AGREEMENT_VERSION, String.valueOf(template.getVersion()));
		LeaseAgreement agreement = agreements.saveAndFlush(new LeaseAgreement(number, facts.holdingId(), facts.orderId(),
				facts.userId(), template.getId(), Placeholder.fill(template.getTitle(), values),
				Placeholder.fill(template.getBody(), values), facts.awaitsAcceptance(), actorId, now));
		audit.record(AuditRecord.of(actorId, AuditAction.LEASE_AGREEMENT_ISSUED, ENTITY, agreement.getId())
			.withNewValue(Map.of("agreementNumber", number, "holdingId", facts.holdingId().toString(),
					"templateVersion", template.getVersion(),
					"acceptanceRequired", facts.awaitsAcceptance())));
		return LeaseAgreementResponse.from(agreement, null);
	}

	/**
	 * Records the investor's acceptance of their holding's agreement, with the lease it started. Called in the
	 * transaction that starts the lease, which has already checked that the holding is theirs and awaiting acceptance.
	 *
	 * @param leaseEndsOn the day after the lease's last day (exclusive)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public LeaseAgreementResponse accept(UUID userId, UUID holdingId, LocalDate leaseStartsOn, LocalDate leaseEndsOn) {
		LeaseAgreement agreement = owned(holdingId, userId);
		if (!agreement.isAcceptanceRequired()) {
			throw new BusinessException(ErrorCode.CONFLICT, "This investment agreement does not need accepting");
		}
		if (acceptances.existsById(agreement.getId())) {
			throw new BusinessException(ErrorCode.CONFLICT, "This investment agreement has already been accepted");
		}
		// What the investor accepts is the wording they were shown: the one now in force.
		Wording accepted = pendingWording(agreement);
		ClientInfo client = ClientInfo.current();
		AgreementAcceptance acceptance = acceptances.saveAndFlush(new AgreementAcceptance(agreement.getId(), userId,
				clock.instant(), leaseStartsOn, leaseEndsOn, client.ipAddress(), client.userAgent(),
				accepted.templateId(), accepted.title(), accepted.body()));
		audit.record(AuditRecord.of(userId, AuditAction.LEASE_AGREEMENT_ACCEPTED, ENTITY, agreement.getId())
			.withNewValue(Map.of("agreementNumber", agreement.getAgreementNumber(), "holdingId", holdingId.toString(),
					"leaseStartsOn", leaseStartsOn.toString(), "templateVersion", accepted.version())));
		return LeaseAgreementResponse.from(agreement, acceptance);
	}

	@Transactional(readOnly = true)
	public List<LeaseAgreementResponse> mine(UUID userId) {
		return responses(agreements.findByUserIdOrderByGeneratedAtDesc(userId));
	}

	@Transactional(readOnly = true)
	public List<LeaseAgreementResponse> byIds(List<UUID> agreementIds) {
		return responses(agreements.findAllById(agreementIds));
	}

	@Transactional(readOnly = true)
	public List<LeaseAgreementResponse> forOrder(UUID orderId) {
		return responses(agreements.findByOrderIdOrderByGeneratedAt(orderId));
	}

	/** A holding's agreement with its wording, for its owner to read (and accept). */
	@Transactional(readOnly = true)
	public LeaseAgreementDocument document(UUID holdingId, UUID ownerId) {
		LeaseAgreement a = owned(holdingId, ownerId);
		AgreementAcceptance acceptance = acceptances.findById(a.getId()).orElse(null);
		Wording w = wording(a, acceptance);
		return new LeaseAgreementDocument(LeaseAgreementResponse.from(a, acceptance, w.title()),
				fillAcceptance(w.body(), a, acceptance));
	}

	/** A holding's agreement as a PDF; with {@code ownerId}, only if that user owns it. */
	@Transactional(readOnly = true)
	public AgreementPdf pdf(UUID holdingId, UUID ownerId) {
		LeaseAgreement a = owned(holdingId, ownerId);
		AgreementAcceptance acceptance = acceptances.findById(a.getId()).orElse(null);
		Wording w = wording(a, acceptance);
		int version = w.version();
		String body = fillAcceptance(w.body(), a, acceptance);
		if (a.isAcceptanceRequired()) {
			body += "\n\n# Acceptance\n" + (acceptance == null
					? "Not yet accepted. The term and returns start when the Investor accepts this Agreement in their SeaLease account."
					: "Accepted electronically by the Investor in their SeaLease account on "
							+ STAMP.format(acceptance.getAcceptedAt().atZone(display.zone()))
							+ (acceptance.getIpAddress() == null ? "" : " from " + acceptance.getIpAddress())
							+ ". The term runs from " + DATE.format(acceptance.getLeaseStartsOn()) + " to "
							+ DATE.format(acceptance.getLeaseEndsOn().minusDays(1)) + ".");
		}
		return new AgreementPdf("agreement-" + a.getAgreementNumber(), renderer.pdf(w.title(),
				a.getAgreementNumber() + " · issued " + DATE.format(LocalDate.ofInstant(a.getGeneratedAt(), display.zone()))
						+ (a.isAcceptanceRequired() && acceptance == null ? " · awaiting acceptance" : ""),
				body, "Issued electronically by SeaLease · " + a.getAgreementNumber() + " · wording version " + version,
				acceptance == null ? null : signature(a, acceptance)));
	}

	/** The accepting investor's signature from their identity verification, with their name and when they accepted. */
	private AgreementRenderer.Signature signature(LeaseAgreement a, AgreementAcceptance acceptance) {
		ProfileResponse investor = profiles.getProfile(a.getUserId());
		return new AgreementRenderer.Signature(kyc.getObject().verifiedSignature(a.getUserId()).orElse(null),
				investor.firstName() + " " + investor.lastName(),
				"Accepted on " + STAMP.format(acceptance.getAcceptedAt().atZone(display.zone())));
	}

	private LeaseAgreement owned(UUID holdingId, UUID ownerId) {
		return agreements.findByHoldingId(holdingId)
			.filter(found -> ownerId == null || found.getUserId().equals(ownerId))
			.orElseThrow(() -> new ResourceNotFoundException("Investment agreement for holding", holdingId));
	}

	private LeaseAgreementResponse response(LeaseAgreement a) {
		return LeaseAgreementResponse.from(a, acceptances.findById(a.getId()).orElse(null));
	}

	private List<LeaseAgreementResponse> responses(List<LeaseAgreement> found) {
		Map<UUID, AgreementAcceptance> accepted = acceptances.findAllById(found.stream().map(LeaseAgreement::getId).toList())
			.stream().collect(Collectors.toMap(AgreementAcceptance::getAgreementId, Function.identity()));
		return found.stream().map(a -> LeaseAgreementResponse.from(a, accepted.get(a.getId()))).toList();
	}

	/** An agreement's title and wording before the lease dates and acceptance details are filled in. */
	private record Wording(UUID templateId, int version, String title, String body) {
	}

	/**
	 * The wording to show: once accepted, the wording accepted; while waiting, the wording now in force; otherwise (no
	 * acceptance needed, or accepted before accepted wording was recorded) the wording issued.
	 */
	private Wording wording(LeaseAgreement a, AgreementAcceptance acceptance) {
		if (acceptance != null && acceptance.getBody() != null) {
			return new Wording(acceptance.getTemplateId(), versionOf(acceptance.getTemplateId()), acceptance.getTitle(),
					acceptance.getBody());
		}
		if (acceptance == null && a.isAcceptanceRequired()) {
			return pendingWording(a);
		}
		return issued(a);
	}

	/** An agreement awaiting acceptance in the wording now in force, filled in with its investment's facts. */
	private Wording pendingWording(LeaseAgreement a) {
		AgreementTemplate current = wording.current();
		if (current.getId().equals(a.getTemplateId())) {
			return issued(a);
		}
		AgreementFacts facts = factsSource.getObject().pendingFacts(a.getHoldingId(), a.getUserId());
		// The agreement keeps the number and date it was issued with.
		Map<Placeholder, String> values = values(facts, a.getAgreementNumber(),
				LocalDate.ofInstant(a.getGeneratedAt(), display.zone()));
		values.put(Placeholder.AGREEMENT_VERSION, String.valueOf(current.getVersion()));
		return new Wording(current.getId(), current.getVersion(), Placeholder.fill(current.getTitle(), values),
				Placeholder.fill(current.getBody(), values));
	}

	private Wording issued(LeaseAgreement a) {
		return new Wording(a.getTemplateId(), versionOf(a.getTemplateId()), a.getTitle(), a.getBody());
	}

	private int versionOf(UUID templateId) {
		return templates.findById(templateId).map(AgreementTemplate::getVersion).orElse(0);
	}

	/**
	 * The wording with the lease dates and acceptance details filled in: those of the acceptance once accepted,
	 * otherwise words saying the lease starts on acceptance. Agreements issued with their dates have no placeholders
	 * left.
	 */
	private String fillAcceptance(String body, LeaseAgreement a, AgreementAcceptance acceptance) {
		Map<Placeholder, String> values = new EnumMap<>(Placeholder.class);
		if (acceptance != null) {
			values.put(Placeholder.LEASE_START_DATE, DATE.format(acceptance.getLeaseStartsOn()));
			values.put(Placeholder.LEASE_END_DATE, DATE.format(acceptance.getLeaseEndsOn().minusDays(1)));
			values.put(Placeholder.ACCEPTANCE_STATUS, "Accepted");
			values.put(Placeholder.ACCEPTED_AT, STAMP.format(acceptance.getAcceptedAt().atZone(display.zone())));
			values.put(Placeholder.ACCEPTANCE_REFERENCE, acceptanceReference(a.getAgreementNumber()));
		}
		else {
			values.put(Placeholder.LEASE_START_DATE, "the day this Agreement is accepted");
			values.put(Placeholder.LEASE_END_DATE, "the day before the same date at the end of the term");
			values.put(Placeholder.ACCEPTANCE_STATUS, "Awaiting acceptance");
			values.put(Placeholder.ACCEPTED_AT, "Not yet accepted");
			values.put(Placeholder.ACCEPTANCE_REFERENCE, "Pending");
		}
		return Placeholder.fill(body, values);
	}

	/** One acceptance per agreement, so its reference follows the agreement's number: AGR-2026-000042 → ACC-2026-000042. */
	private static String acceptanceReference(String agreementNumber) {
		return agreementNumber.replaceFirst("^AGR-", "ACC-");
	}

	private Map<Placeholder, String> values(AgreementFacts f, String number, LocalDate today) {
		ProfileResponse investor = profiles.getProfile(f.userId());
		Map<Placeholder, String> v = new EnumMap<>(Placeholder.class);
		v.put(Placeholder.AGREEMENT_NUMBER, number);
		v.put(Placeholder.AGREEMENT_DATE, DATE.format(today));
		v.put(Placeholder.CURRENT_YEAR, String.valueOf(today.getYear()));
		v.put(Placeholder.COMPANY_NAME, company.issuerName());
		v.put(Placeholder.COMPANY_ADDRESS, company.issuerAddress());
		v.put(Placeholder.INVESTOR_NAME, (investor.firstName() + " " + investor.lastName()).strip());
		v.put(Placeholder.INVESTOR_CODE, orDash(investor.userCode()));
		v.put(Placeholder.INVESTOR_EMAIL, investor.email());
		v.put(Placeholder.INVESTOR_ADDRESS, address(investor.address()));
		v.put(Placeholder.ORDER_NUMBER, orDash(f.orderNumber()));
		v.put(Placeholder.PLAN_CODE, orDash(f.planCode()));
		v.put(Placeholder.PLAN_TITLE, orDash(f.planTitle()));
		v.put(Placeholder.PURCHASE_PRICE, f.purchasePrice().display());
		v.put(Placeholder.ANNUAL_RETURNS_PERCENT, percent(f.monthlyRentPercent().multiply(BigDecimal.valueOf(12))));
		v.put(Placeholder.MONTHLY_RENT_PERCENT, percent(f.monthlyRentPercent()));
		v.put(Placeholder.MONTHLY_RENT, f.monthlyRent().display());
		v.put(Placeholder.TOTAL_RENT, f.monthlyRent().times(BigDecimal.valueOf(f.tenureMonths())).display());
		v.put(Placeholder.TENURE_MONTHS, String.valueOf(f.tenureMonths()));
		v.put(Placeholder.TENURE_YEARS, BigDecimal.valueOf(f.tenureMonths())
			.divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
		// Awaiting acceptance, the dates are not known yet: their placeholders stay, filled in when shown.
		if (!f.awaitsAcceptance()) {
			v.put(Placeholder.LEASE_START_DATE, DATE.format(f.leaseStartsOn()));
			v.put(Placeholder.LEASE_END_DATE, DATE.format(f.leaseEndsOn().minusDays(1)));
			// Issued with its dates, the agreement is never accepted.
			v.put(Placeholder.ACCEPTANCE_STATUS, "Not required");
			v.put(Placeholder.ACCEPTED_AT, "Not applicable");
			v.put(Placeholder.ACCEPTANCE_REFERENCE, "Not applicable");
		}
		return v;
	}

	private static String address(ProfileResponse.Address a) {
		if (a == null) {
			return "address not given";
		}
		String cityLine = Stream.of(a.postalCode(), a.city()).filter(s -> s != null && !s.isBlank())
			.collect(Collectors.joining(" "));
		String joined = Stream.of(a.line1(), a.line2(), cityLine, a.stateRegion(), a.country())
			.filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty())
			.collect(Collectors.joining(", "));
		return joined.isEmpty() ? "address not given" : joined;
	}

	private static String orDash(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}

	/** "15%", "1.33%": two decimals at most, as a rate reads in a contract. */
	private static String percent(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
	}

}
