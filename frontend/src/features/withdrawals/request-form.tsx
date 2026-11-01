"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { EarningsBalance } from "@/types/earning";
import type { FormState } from "@/validators/form-state";
import { requestWithdrawalAction } from "./actions";

export interface RequestableWallet {
  balance: EarningsBalance;
  label: string;
  /** The whole wallet, formatted, and how it is paid. */
  amount: string;
  to: string;
}

/** An online investor's request for the whole of one wallet. */
export function WithdrawalRequestForm({ wallets, idempotencyKey }: { wallets: RequestableWallet[]; idempotencyKey: string }) {
  const [state, action] = useActionState<FormState, FormData>(requestWithdrawalAction.bind(null, idempotencyKey), {});
  const [chosen, setBalance] = useState<EarningsBalance | null>(null);
  // The first wallet until one is chosen; a wallet that is no longer offered falls back to it too.
  const balance = wallets.some((w) => w.balance === chosen) ? chosen : wallets[0]?.balance;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-2">
        <legend className="sr-only">Wallet to withdraw</legend>
        {wallets.map((w) => (
          <label key={w.balance} className="flex items-start gap-2 text-sm">
            <input type="radio" name="balance" value={w.balance} checked={balance === w.balance}
              onChange={() => setBalance(w.balance)} className="mt-1" />
            <span>
              {w.label}: <strong className="tabular-nums">{w.amount}</strong>
              <span className="block text-xs text-muted">{w.to}</span>
            </span>
          </label>
        ))}
      </fieldset>
      <SubmitButton confirm="Withdraw the whole of this wallet?">Request withdrawal</SubmitButton>
    </form>
  );
}
