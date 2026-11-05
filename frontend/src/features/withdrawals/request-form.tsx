"use client";

import Link from "next/link";
import { ArrowLeft, ArrowRight, Check, CircleCheckBig, Landmark, Loader2, ShieldCheck, Wallet } from "lucide-react";
import { useActionState, useEffect, useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { EarningsBalance } from "@/types/earning";
import type { Money } from "@/types/plan";
import type { WithdrawalOverview, WithdrawalQuote } from "@/types/withdrawal";
import { formatMoney } from "@/utils/money";
import type { FormState } from "@/validators/form-state";
import { quoteWithdrawalAction, requestWithdrawalAction } from "./actions";
import { Breakdown, deduction } from "./ui";

export interface RequestableWallet {
  balance: EarningsBalance;
  label: string;
  available: Money;
}

type BankAccount = WithdrawalOverview["bankAccounts"][number];

const STEPS = ["Wallet", "Amount", "Bank account", "Review", "Done"] as const;
const SHARES = [25, 50, 75] as const;

/**
 * An online investor's withdrawal, step by step: the wallet, the amount (quoted by the backend with charges and tax),
 * the verified bank account to pay, then a review confirmed with their password. Nothing here is trusted: the backend
 * works the figures out again when the request is submitted.
 */
export function WithdrawalRequestForm({ wallets, bankAccounts, cash, minimum, idempotencyKey }: {
  wallets: RequestableWallet[];
  bankAccounts: BankAccount[];
  /** Paid in cash at the office: no bank account step. */
  cash: boolean;
  minimum: Money;
  idempotencyKey: string;
}) {
  const [state, action] = useActionState<FormState, FormData>(requestWithdrawalAction.bind(null, idempotencyKey), {});
  const [step, setStep] = useState(0);
  const [balance, setBalance] = useState<EarningsBalance>(wallets[0]?.balance ?? "RENT_BANK");
  const [amount, setAmount] = useState("");
  const [quoted, setQuote] = useState<WithdrawalQuote | null>(null);
  const [quotedError, setQuoteError] = useState<string | null>(null);
  const [quoting, startQuote] = useTransition();
  const [accountId, setAccountId] = useState(bankAccounts.find((a) => a.primary)?.id ?? bankAccounts[0]?.id ?? "");
  // A quote only stands for the amount (and wallet) it was made for.
  const fresh = amount.trim() !== "" && quoted?.balance === balance
    && (!quoted.allowed || Number(quoted.amount.amount) === Number(amount));
  const quote = fresh ? quoted : null;
  const quoteError = amount.trim() ? quotedError : null;
  const wallet: RequestableWallet = wallets.find((w) => w.balance === balance) ?? wallets[0] ?? { balance, label: "Wallet", available: minimum };
  const account = bankAccounts.find((a) => a.id === accountId) ?? null;
  const submitted = Boolean(state.success && state.values?.id);
  const current = submitted ? 4 : step;
  const visibleSteps = STEPS.filter((_, i) => !(cash && i === 2));

  // A fresh quote whenever the amount settles; the last one wins.
  useEffect(() => {
    if (!amount.trim()) return;
    let live = true;
    const timer = setTimeout(() => startQuote(async () => {
      const result = await quoteWithdrawalAction(balance, amount);
      if (!live) return;
      if ("error" in result) {
        setQuote(null);
        setQuoteError(result.error);
      } else {
        setQuote(result);
        setQuoteError(null);
      }
    }), 350);
    return () => { live = false; clearTimeout(timer); };
  }, [amount, balance]);

  const next = () => setStep((s) => (s === 1 && cash ? 3 : s + 1));
  const back = () => setStep((s) => (s === 3 && cash ? 1 : s - 1));
  const share = (pct: number) => {
    const value = Math.floor(Number(wallet.available.amount) * pct) / 100;
    setAmount(value.toFixed(2));
  };

  return (
    <div className="space-y-6">
      {/* Step bar */}
      <ol className="flex items-center" aria-label="Steps">
        {visibleSteps.map((label, position) => {
          const index = STEPS.indexOf(label);
          const done = index < current;
          const active = index === current;
          return (
            <li key={label} aria-current={active ? "step" : undefined} className="flex flex-1 items-center last:flex-none">
              <div className="flex items-center gap-2">
                <span className={`flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                  done ? "bg-gold text-on-gold" : active ? "bg-gold/10 text-gold-text ring-2 ring-gold" : "bg-background text-muted ring-1 ring-border"}`}>
                  {done ? <Check className="size-3.5" aria-hidden="true" /> : position + 1}
                </span>
                <span className={`hidden text-xs sm:inline ${active ? "font-semibold" : done ? "text-foreground" : "text-muted"}`}>{label}</span>
              </div>
              {position < visibleSteps.length - 1 && (
                <span aria-hidden="true" className={`mx-2 h-px flex-1 ${done ? "bg-gold" : "bg-border"}`} />
              )}
            </li>
          );
        })}
      </ol>

      {current === 0 && (
        <fieldset className="space-y-4">
          <legend className="mb-3 text-sm font-medium">Which wallet do you want to withdraw from?</legend>
          <div className="grid gap-3 sm:grid-cols-2">
            {wallets.map((w) => {
              const selected = balance === w.balance;
              return (
                <label key={w.balance}
                  className={`relative flex cursor-pointer flex-col gap-3 rounded-xl border p-4 transition-all ${selected ? "border-gold bg-gold/5 ring-1 ring-gold" : "border-border hover:border-foreground/30"}`}>
                  <input type="radio" name="wallet" value={w.balance} checked={selected} className="sr-only"
                    onChange={() => setBalance(w.balance)} />
                  <span className="flex items-center justify-between">
                    <span className="flex size-9 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><Wallet className="size-4.5" aria-hidden="true" /></span>
                    <span className={`flex size-5 items-center justify-center rounded-full border-2 ${selected ? "border-gold bg-gold text-on-gold" : "border-border"}`}>
                      {selected && <Check className="size-3" aria-hidden="true" />}
                    </span>
                  </span>
                  <span>
                    <span className="block text-sm text-muted">{w.label}</span>
                    <span className="block font-display text-xl font-semibold tabular-nums">{formatMoney(w.available)}</span>
                  </span>
                </label>
              );
            })}
          </div>
          <Nav onNext={next} />
        </fieldset>
      )}

      {current === 1 && (
        <div className="space-y-5">
          <div className="space-y-2">
            <label htmlFor="withdraw-amount" className="flex items-baseline justify-between text-sm font-medium">
              How much?
              <span className="text-xs font-normal text-muted">Available {formatMoney(wallet.available)}</span>
            </label>
            <div className={`flex items-center rounded-xl border bg-surface px-4 transition-colors focus-within:border-gold focus-within:ring-1 focus-within:ring-gold ${quoteError || quote?.problem ? "border-rose-500" : "border-border"}`}>
              <span className="font-display text-2xl text-muted" aria-hidden="true">₹</span>
              <input id="withdraw-amount" inputMode="decimal" autoComplete="off" value={amount} placeholder="0.00"
                onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ""))}
                aria-invalid={quoteError || quote?.problem ? true : undefined} aria-describedby="withdraw-amount-help"
                className="h-16 w-full bg-transparent px-2 font-display text-3xl font-semibold tabular-nums outline-none" />
              {quoting && <Loader2 className="size-5 animate-spin text-muted" aria-label="Working it out" />}
            </div>
            <div className="flex flex-wrap gap-2">
              {SHARES.map((pct) => (
                <button key={pct} type="button" onClick={() => share(pct)}
                  className="rounded-full border border-border px-3 py-1 text-xs font-medium hover:border-gold hover:text-gold-text">{pct}%</button>
              ))}
              <button type="button" onClick={() => setAmount(wallet.available.amount)}
                className="rounded-full border border-gold bg-gold/5 px-3 py-1 text-xs font-medium text-gold-text hover:bg-gold/10">Max</button>
            </div>
            <p id="withdraw-amount-help" className={`text-xs ${quoteError || quote?.problem ? "text-rose-600 dark:text-rose-400" : "text-muted"}`}>
              {quoteError ?? quote?.problem ?? `Minimum ${formatMoney(minimum)}. Charges and tax are worked out by us.`}
            </p>
          </div>
          {quote?.allowed && (
            <Breakdown rows={[
              { label: "Amount withdrawn", value: formatMoney(quote.amount) },
              { label: "Charges", value: deduction(quote.fee), muted: true },
              { label: "Tax withheld (TDS)", value: deduction(quote.tax), muted: true },
              { label: "You receive", value: formatMoney(quote.net), strong: true },
              { label: "Left in wallet", value: formatMoney(quote.remaining), muted: true },
            ]} />
          )}
          <Nav onBack={back} onNext={next} disabled={!quote?.allowed || quoting} />
        </div>
      )}

      {current === 2 && (
        <fieldset className="space-y-4">
          <legend className="mb-3 text-sm font-medium">Where should we pay it?</legend>
          {bankAccounts.length === 0 ? (
            <p className="text-sm text-muted">
              You need a verified bank account. <Link href="/profile/bank-accounts" className="underline">Add one</Link>
            </p>
          ) : (
            <div className="space-y-3">
              {bankAccounts.map((a) => {
                const selected = accountId === a.id;
                return (
                  <label key={a.id}
                    className={`flex cursor-pointer items-center gap-4 rounded-xl border p-4 text-sm transition-all ${selected ? "border-gold bg-gold/5 ring-1 ring-gold" : "border-border hover:border-foreground/30"}`}>
                    <input type="radio" name="account" value={a.id} checked={selected} onChange={() => setAccountId(a.id)} className="sr-only" />
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-background text-gold-text"><Landmark className="size-5" aria-hidden="true" /></span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-medium">{a.bankName ?? "Bank account"} <span className="font-mono text-muted">{a.accountMasked}</span></span>
                      <span className="flex flex-wrap items-center gap-2 text-xs text-muted">
                        {a.holderName}
                        <span className="inline-flex items-center gap-1 text-emerald-700 dark:text-emerald-400"><ShieldCheck className="size-3" aria-hidden="true" /> Verified</span>
                        {a.primary && <span className="rounded bg-background px-1.5 py-0.5 text-[10px] font-medium uppercase">Primary</span>}
                      </span>
                    </span>
                    <span className={`flex size-5 items-center justify-center rounded-full border-2 ${selected ? "border-gold bg-gold text-on-gold" : "border-border"}`}>
                      {selected && <Check className="size-3" aria-hidden="true" />}
                    </span>
                  </label>
                );
              })}
            </div>
          )}
          <Nav onBack={back} onNext={next} disabled={!account} />
        </fieldset>
      )}

      {current === 3 && quote && (
        <form action={action} className="space-y-5" noValidate>
          <FormFeedback state={state.success ? {} : state} />
          <input type="hidden" name="balance" value={balance} />
          <input type="hidden" name="amount" value={quote.amount.amount} />
          {!cash && <input type="hidden" name="bankAccountId" value={accountId} />}
          <div className="rounded-xl border border-border bg-background/60 p-5 text-center">
            <p className="text-sm text-muted">You receive</p>
            <p className="font-display text-4xl font-semibold tracking-tight tabular-nums">{formatMoney(quote.net)}</p>
            <p className="mt-1 text-sm text-muted">
              from your {wallet.label.toLowerCase()} to {cash ? "cash at our office" : account ? <>{account.bankName} <span className="font-mono">{account.accountMasked}</span></> : "—"}
            </p>
          </div>
          <Breakdown rows={[
            { label: "Amount withdrawn", value: formatMoney(quote.amount) },
            { label: "Charges", value: deduction(quote.fee), muted: true },
            { label: "Tax withheld (TDS)", value: deduction(quote.tax), muted: true },
            { label: "You receive", value: formatMoney(quote.net), strong: true },
          ]} />
          <TextField label="Confirm with your password" name="password" type="password" autoComplete="current-password" required
            error={state.fieldErrors?.password} />
          <label className="flex items-start gap-3 rounded-lg border border-border p-3 text-sm">
            <input type="checkbox" name="confirmed" className="mt-0.5 size-4 accent-[var(--gold)]" required />
            <span className="text-muted">I confirm the amount and account are correct. The amount is held from my wallet until the withdrawal is paid, rejected or cancelled.</span>
          </label>
          {state.fieldErrors?.confirmed && <p className="text-xs text-rose-600">{state.fieldErrors.confirmed}</p>}
          <div className="flex items-center justify-between gap-3">
            <Button type="button" variant="quiet" onClick={back}><ArrowLeft className="size-4" aria-hidden="true" /> Back</Button>
            <SubmitButton pendingLabel="Submitting…">Submit withdrawal</SubmitButton>
          </div>
        </form>
      )}

      {current === 4 && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-emerald-500/30 bg-emerald-500/5 px-6 py-10 text-center">
          <span className="flex size-14 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400">
            <CircleCheckBig className="size-7" aria-hidden="true" />
          </span>
          <p className="text-lg font-semibold">Withdrawal submitted</p>
          <p className="font-mono text-sm">{state.values?.reference}</p>
          <p className="max-w-md text-sm text-muted">The amount is held from your wallet while our finance team reviews it. We will notify you at every step.</p>
          <Link href={`/withdrawals/${state.values?.id}`}
            className="mt-2 inline-flex min-h-11 items-center gap-2 rounded-lg bg-gold px-5 text-sm font-medium text-on-gold hover:bg-gold-hover">
            Track this withdrawal <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </div>
      )}
    </div>
  );
}

function Nav({ onBack, onNext, disabled }: { onBack?: () => void; onNext: () => void; disabled?: boolean }) {
  return (
    <div className={`flex items-center gap-3 ${onBack ? "justify-between" : "justify-end"}`}>
      {onBack && <Button type="button" variant="quiet" onClick={onBack}><ArrowLeft className="size-4" aria-hidden="true" /> Back</Button>}
      <Button type="button" onClick={onNext} disabled={disabled}>Continue <ArrowRight className="size-4" aria-hidden="true" /></Button>
    </div>
  );
}
