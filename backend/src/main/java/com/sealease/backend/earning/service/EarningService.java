package com.sealease.backend.earning.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.EarningResponse;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.entity.Earning;
import com.sealease.backend.earning.entity.RentalReceipt;
import com.sealease.backend.earning.repository.EarningRepository;
import com.sealease.backend.earning.repository.HoldingEarningsTotal;
import com.sealease.backend.earning.repository.RentalReceiptRepository;
import com.sealease.backend.investment.dto.Lease;
import com.sealease.backend.investment.service.LeaseService;
import com.sealease.backend.ledger.dto.LedgerAccountResponse;
import com.sealease.backend.ledger.service.LedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The investor's view of their rental income. */
@Service
public class EarningService {

	private final EarningRepository earnings;
	private final RentalReceiptRepository receipts;
	private final LeaseService leases;
	private final LedgerService ledger;

	public EarningService(EarningRepository earnings, RentalReceiptRepository receipts, LeaseService leases,
			LedgerService ledger) {
		this.earnings = earnings;
		this.receipts = receipts;
		this.leases = leases;
		this.ledger = ledger;
	}

	@Transactional(readOnly = true)
	public EarningsSummary summary(UUID userId) {
		List<MoneyResponse> balances = ledger.investorAccounts(userId).stream()
			.map(LedgerAccountResponse::balance)
			.toList();
		List<HoldingEarningsTotal> perHolding = earnings.totalsByHolding(userId);
		Map<String, Money> totals = new TreeMap<>();
		for (HoldingEarningsTotal t : perHolding) {
			totals.merge(t.currency(), Money.of(t.net(), Currency.getInstance(t.currency())), Money::plus);
		}
		return new EarningsSummary(balances, totals.values().stream().map(MoneyResponse::from).toList(),
				perHolding.stream()
					.map(t -> new EarningsSummary.HoldingEarnings(t.holdingId(), t.productId(),
							MoneyResponse.from(Money.of(t.net(), Currency.getInstance(t.currency()))), t.payments(),
							t.lastPaidAt()))
					.toList());
	}

	@Transactional(readOnly = true)
	public Page<EarningResponse> history(UUID userId, Pageable pageable) {
		Page<Earning> page = earnings.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
		Map<UUID, Lease> products = leases.leases(page.map(Earning::getProductId).toSet());
		Map<UUID, RentalReceipt> periods = receipts.findAllById(page.map(Earning::getReceiptId).toSet()).stream()
			.collect(Collectors.toMap(RentalReceipt::getId, Function.identity()));
		return page.map(e -> {
			Lease lease = products.get(e.getProductId());
			RentalReceipt receipt = periods.get(e.getReceiptId());
			return new EarningResponse(e.getId(), e.getHoldingId(), e.getProductId(), lease.code(), lease.title(),
					e.getPeriodNumber(), receipt.getPeriodStartsOn(), receipt.getPeriodEndsOn(), e.getOwnershipPercent(),
					MoneyResponse.from(e.gross()), MoneyResponse.from(e.fee()), MoneyResponse.from(e.net()),
					e.getCreatedAt());
		});
	}

}
