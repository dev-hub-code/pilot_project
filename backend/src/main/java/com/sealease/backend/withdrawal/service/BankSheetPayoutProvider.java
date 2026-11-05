package com.sealease.backend.withdrawal.service;

import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The payout provider in use today: approved withdrawals go into a payout batch with its Excel sheet (bank transfers)
 * or a cash list (office cash), as withdrawal runs do. Each is then a finance task, completed from the reconciled
 * sheet or with the bank's reference.
 */
@Component
public class BankSheetPayoutProvider implements PayoutProvider {

	public static final String NAME = "BANK_SHEET";

	private final PayoutBatchService batches;

	public BankSheetPayoutProvider(PayoutBatchService batches) {
		this.batches = batches;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public WithdrawalBatch initiate(WithdrawalKind kind, WithdrawalPayoutMethod method, List<Withdrawal> items,
			UUID actorId) {
		// Named after the request when there is one, as a run's sheet is named after the run.
		String label = items.size() == 1 ? items.getFirst().getReference() : "PAY-" + items.getFirst().getReference();
		return batches.createForRun(kind, method, items, label, actorId);
	}

}
