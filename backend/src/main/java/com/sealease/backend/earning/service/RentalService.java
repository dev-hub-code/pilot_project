package com.sealease.backend.earning.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.DuePeriodResponse;
import com.sealease.backend.earning.dto.RecordRentalRequest;
import com.sealease.backend.earning.dto.RentalReceiptDetail;
import com.sealease.backend.earning.dto.RentalReceiptResponse;
import com.sealease.backend.earning.dto.RentalSearchCriteria;
import com.sealease.backend.earning.entity.Earning;
import com.sealease.backend.earning.entity.ReceiptStatus;
import com.sealease.backend.earning.entity.RentalReceipt;
import com.sealease.backend.earning.repository.EarningRepository;
import com.sealease.backend.earning.repository.RentalReceiptRepository;
import com.sealease.backend.investment.dto.Lease;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.service.LeaseService;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rental income: finance records what each lessee paid per period, a second person approves it,
 * and approval distributes it to the offering's investors by ownership.
 *
 * <p>Lock order is <em>receipt, then offering</em>. Recording locks only the offering (there is no
 * receipt yet), so a period can never be recorded twice and distributions of one lease serialise.
 * Distribution writes the earnings, the balanced ledger transaction and the outbox events in one
 * transaction.
 */
@Service
public class RentalService {

	private static final String ENTITY = "RENTAL_RECEIPT";

	private final RentalReceiptRepository receipts;
	private final EarningRepository earnings;
	private final LeaseService leases;
	private final LedgerService ledger;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final Clock clock;

	public RentalService(RentalReceiptRepository receipts, EarningRepository earnings, LeaseService leases,
			LedgerService ledger, OutboxPublisher outbox, AuditService audit, Clock clock) {
		this.receipts = receipts;
		this.earnings = earnings;
		this.leases = leases;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public RentalReceiptDetail record(UUID actorId, RecordRentalRequest request) {
		Lease lease = leases.lock(request.productId());
		if (lease.status() != ProductStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Rent can only be recorded for an offering on lease");
		}
		Lease.RentalPeriod period = lease.period(request.periodNumber())
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The lease has rental periods 1 to " + lease.periodCount()));
		LocalDate today = today();
		if (request.receivedOn().isAfter(today)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The payment date cannot be in the future");
		}
		if (request.receivedOn().isBefore(lease.startsOn())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The payment date is before the lease started");
		}
		int digits = Math.max(lease.currency().getDefaultFractionDigits(), 0);
		if (request.amount().stripTrailingZeros().scale() > digits) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Amounts in " + lease.currency().getCurrencyCode() + " allow at most " + digits + " decimals");
		}
		Money amount = Money.of(request.amount(), lease.currency());
		String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
		if (!amount.equals(lease.expectedRental()) && note == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The expected rent is "
					+ lease.expectedRental().display() + "; explain the difference in a note");
		}
		if (receipts.existsByProductIdAndPeriodNumberAndStatusNot(lease.productId(), period.number(),
				ReceiptStatus.REJECTED)) {
			throw new BusinessException(ErrorCode.CONFLICT, "Rent for period " + period.number() + " is already recorded");
		}

		RentalReceipt receipt = receipts.saveAndFlush(new RentalReceipt(lease.productId(), period.number(),
				period.startsOn(), period.endsOn(), amount, lease.expectedRental(), request.receivedOn(),
				request.externalReference().strip(), note, actorId));
		audit.record(AuditRecord.of(actorId, AuditAction.RENTAL_RECORDED, ENTITY, receipt.getId())
			.withNewValue(Map.of("productId", lease.productId().toString(), "period", period.number(),
					"amount", amount.toString(), "reference", receipt.getExternalReference())));
		return detail(receipt, lease);
	}

	/**
	 * A second finance user releases the money to investors: earnings are credited, the ledger is
	 * posted and, after the lease's last period, the offering matures.
	 */
	@Transactional
	public RentalReceiptDetail approve(UUID actorId, UUID receiptId) {
		RentalReceipt receipt = lockReceipt(receiptId);
		if (receipt.getStatus() != ReceiptStatus.RECORDED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This rental payment is already " + receipt.getStatus());
		}
		if (receipt.getRecordedBy().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					"A rental payment must be approved by someone other than the person who recorded it");
		}
		Lease lease = leases.lock(receipt.getProductId());
		if (lease.status() != ProductStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "The offering is no longer on lease");
		}
		Instant now = clock.instant();
		RentalSplit split = RentalSplit.of(receipt.amount(), lease.price(), lease.managementFeePercent(),
				leases.holders(lease.productId()));

		UUID transactionId = ledger.post(TransactionType.RENTAL_DISTRIBUTION, receiptId.toString(),
				"Rent %s period %d of %d".formatted(lease.code(), receipt.getPeriodNumber(), lease.periodCount()),
				actorId, postings(split));
		List<Earning> credited = earnings.saveAll(split.shares().stream()
			.map(share -> new Earning(receiptId, lease.productId(), receipt.getPeriodNumber(), share, now))
			.toList());
		receipt.distribute(actorId, lease.managementFeePercent(), transactionId, now);
		receipts.flush();

		Money toInvestors = split.received().minus(split.fees()).minus(split.retained());
		audit.record(AuditRecord.of(actorId, AuditAction.RENTAL_DISTRIBUTED, ENTITY, receiptId)
			.withNewValue(Map.of("amount", split.received().toString(), "toInvestors", toInvestors.toString(),
					"fees", split.fees().toString(), "retained", split.retained().toString(),
					"holdings", credited.size(), "ledgerTransactionId", transactionId.toString())));
		outbox.publish(DomainEvent.of(KafkaTopics.RENTAL_GENERATED, "RentalDistributed", ENTITY, receiptId,
				Map.of("receiptId", receiptId.toString(), "productId", lease.productId().toString(),
						"periodNumber", receipt.getPeriodNumber(), "amount", split.received().amount().toPlainString(),
						"toInvestors", toInvestors.amount().toPlainString(), "fees", split.fees().amount().toPlainString(),
						"retained", split.retained().amount().toPlainString(),
						"currency", lease.currency().getCurrencyCode())));
		for (Earning earning : credited) {
			outbox.publish(DomainEvent.of(KafkaTopics.EARNING_CREATED, "EarningCreated", "EARNING", earning.getId(),
					Map.of("earningId", earning.getId().toString(), "userId", earning.getUserId().toString(),
							"holdingId", earning.getHoldingId().toString(), "productId", lease.productId().toString(),
							"periodNumber", earning.getPeriodNumber(), "net", earning.net().amount().toPlainString(),
							"currency", lease.currency().getCurrencyCode())));
		}

		if (receipts.countByProductIdAndStatus(lease.productId(), ReceiptStatus.DISTRIBUTED) == lease.periodCount()) {
			leases.mature(lease.productId());
		}
		return detail(receipt, lease);
	}

	/**
	 * Voids a recorded payment (wrong period, amount or reference) so it can be recorded again.
	 * Approvers may reject any; others only what they recorded themselves.
	 */
	@Transactional
	public RentalReceiptDetail reject(UUID actorId, boolean approver, UUID receiptId, String reason) {
		RentalReceipt receipt = lockReceipt(receiptId);
		if (!approver && !receipt.getRecordedBy().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You can only void rental payments you recorded");
		}
		if (receipt.getStatus() != ReceiptStatus.RECORDED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This rental payment is already " + receipt.getStatus());
		}
		receipt.reject(actorId, reason.strip(), clock.instant());
		receipts.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.RENTAL_REJECTED, ENTITY, receiptId)
			.withOldValue(Map.of("status", ReceiptStatus.RECORDED))
			.withNewValue(Map.of("status", ReceiptStatus.REJECTED, "reason", reason.strip())));
		return detail(receipt, leases.lease(receipt.getProductId()));
	}

	// ------------------------------------------------------------------------------ queries

	@Transactional(readOnly = true)
	public Page<RentalReceiptResponse> search(RentalSearchCriteria criteria, Pageable pageable) {
		Page<RentalReceipt> page = receipts.findAll(matching(criteria), pageable);
		Map<UUID, Lease> byId = leases.leases(page.map(RentalReceipt::getProductId).toSet());
		return page.map(r -> RentalReceiptResponse.from(r, byId.get(r.getProductId())));
	}

	@Transactional(readOnly = true)
	public RentalReceiptDetail detail(UUID receiptId) {
		RentalReceipt receipt = receipts.findById(receiptId)
			.orElseThrow(() -> new ResourceNotFoundException("Rental payment", receiptId));
		return detail(receipt, leases.lease(receipt.getProductId()));
	}

	/** Periods of offerings on lease whose rent has fallen due but has not been recorded, oldest first. */
	@Transactional(readOnly = true)
	public List<DuePeriodResponse> due() {
		List<Lease> active = leases.active();
		Set<String> recorded = receipts.findByProductIdInAndStatusNot(active.stream().map(Lease::productId).toList(),
				ReceiptStatus.REJECTED).stream()
			.map(r -> r.getProductId() + "#" + r.getPeriodNumber())
			.collect(Collectors.toSet());
		LocalDate today = today();
		List<DuePeriodResponse> due = new ArrayList<>();
		for (Lease lease : active) {
			for (int n = 1; n <= lease.periodCount(); n++) {
				Lease.RentalPeriod period = lease.period(n).orElseThrow();
				if (!period.isDueOn(today)) {
					break;
				}
				if (!recorded.contains(lease.productId() + "#" + n)) {
					due.add(new DuePeriodResponse(lease.productId(), lease.code(), lease.title(), n, lease.periodCount(),
							period.startsOn(), period.endsOn(), MoneyResponse.from(lease.expectedRental()),
							ChronoUnit.DAYS.between(period.endsOn(), today)));
				}
			}
		}
		due.sort(Comparator.comparing(DuePeriodResponse::dueOn).thenComparing(DuePeriodResponse::productCode));
		return due;
	}

	// ----------------------------------------------------------------------------- internal

	/**
	 * Cash in on one side; each investor's net, the fees and the retained remainder on the other.
	 * Several holdings of one investor are credited to their account as one posting.
	 */
	private static List<Posting> postings(RentalSplit split) {
		List<Posting> postings = new ArrayList<>();
		postings.add(Posting.debit(AccountType.RENTAL_CASH, null, split.received()));
		Map<UUID, Money> perInvestor = new LinkedHashMap<>();
		for (RentalSplit.Share share : split.shares()) {
			perInvestor.merge(share.userId(), share.net(), Money::plus);
		}
		perInvestor.forEach((userId, net) -> postings.add(Posting.credit(AccountType.INVESTOR_EARNINGS, userId, net)));
		postings.add(Posting.credit(AccountType.PLATFORM_FEE_REVENUE, null, split.fees()));
		postings.add(Posting.credit(AccountType.PLATFORM_RETAINED, null, split.retained()));
		return postings;
	}

	/** Distributed receipts show what was paid; recorded ones preview the split approval would make. */
	private RentalReceiptDetail detail(RentalReceipt receipt, Lease lease) {
		RentalReceiptResponse view = RentalReceiptResponse.from(receipt, lease);
		Money received = receipt.amount();
		List<RentalReceiptDetail.Line> lines = new ArrayList<>();
		Money gross = Money.zero(received.currency());
		Money fees = Money.zero(received.currency());
		boolean preview = receipt.getStatus() == ReceiptStatus.RECORDED;
		if (receipt.getStatus() == ReceiptStatus.DISTRIBUTED) {
			for (Earning e : earnings.findByReceiptIdOrderByOwnershipPercentDescIdAsc(receipt.getId())) {
				lines.add(new RentalReceiptDetail.Line(e.getHoldingId(), e.getUserId(), e.getOwnershipPercent(),
						MoneyResponse.from(e.gross()), MoneyResponse.from(e.fee()), MoneyResponse.from(e.net())));
				gross = gross.plus(e.gross());
				fees = fees.plus(e.fee());
			}
		}
		else if (preview) {
			RentalSplit split = RentalSplit.of(received, lease.price(), lease.managementFeePercent(),
					leases.holders(lease.productId()));
			for (RentalSplit.Share s : split.shares()) {
				lines.add(new RentalReceiptDetail.Line(s.holdingId(), s.userId(), s.ownershipPercent(),
						MoneyResponse.from(s.gross()), MoneyResponse.from(s.fee()), MoneyResponse.from(s.net())));
				gross = gross.plus(s.gross());
				fees = fees.plus(s.fee());
			}
			lines.sort(Comparator.comparing(RentalReceiptDetail.Line::ownershipPercent).reversed());
		}
		else {
			return new RentalReceiptDetail(view, false, List.of(), null, null, null);
		}
		return new RentalReceiptDetail(view, preview, lines, MoneyResponse.from(gross.minus(fees)),
				MoneyResponse.from(fees), MoneyResponse.from(received.minus(gross)));
	}

	private RentalReceipt lockReceipt(UUID receiptId) {
		return receipts.findByIdForUpdate(receiptId)
			.orElseThrow(() -> new ResourceNotFoundException("Rental payment", receiptId));
	}

	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private static Specification<RentalReceipt> matching(RentalSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.productId() != null) {
				predicates.add(cb.equal(root.get("productId"), c.productId()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
