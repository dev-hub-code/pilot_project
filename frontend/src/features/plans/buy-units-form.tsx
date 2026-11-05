"use client";

import Link from "next/link";
import { useActionState, useEffect, useRef, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { placeOrderAction } from "@/features/orders/actions";
import { overCheckoutLimit } from "@/features/orders/checkout-limit";
import { yearsLabel } from "@/features/orders/lease-years";
import type { Money, PlanQuote } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";
import type { FormState } from "@/validators/form-state";
import { quotePlanAction } from "./actions";

const UNITS = /^\d{1,9}$/;
const DEBOUNCE_MS = 350;

/**
 * The investor enters how many units to buy and sees what they cost and pay, worked out by the backend as they type.
 * With {@code canBuy} they accept the terms and place the order; otherwise the form is only an estimate.
 */
export function BuyUnitsForm({ productId, tenureYears, initial, idempotencyKey, canBuy, checkoutLimit, payableOtherwise = false }: {
  productId: string;
  tenureYears: number;
  initial: PlanQuote;
  idempotencyKey: string;
  canBuy: boolean;
  /** The most one online checkout payment can be, when there is a limit. */
  checkoutLimit?: Money;
  /** The investor has another way to pay than the checkout (e.g. bank transfer). */
  payableOtherwise?: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(placeOrderAction.bind(null, productId, idempotencyKey), {});
  const [units, setUnits] = useState(state.values?.units ?? String(initial.units));
  const [quote, setQuote] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const latest = useRef(0);

  useEffect(() => () => clearTimeout(timer.current), []);

  function change(next: string) {
    setUnits(next);
    clearTimeout(timer.current);
    latest.current++;
    setPending(false);
    if (next === "") {
      setError(null);
      return;
    }
    const value = Number(next);
    if (!UNITS.test(next) || value < 1 || value > 100_000_000) {
      setError("Enter a whole number of units.");
      return;
    }
    setError(null);
    timer.current = setTimeout(async () => {
      const request = latest.current;
      setPending(true);
      const result = await quotePlanAction(productId, value);
      if (request !== latest.current) return; // a newer change is on its way
      setPending(false);
      if (result) setQuote(result);
      else setError("Couldn't calculate right now. Please try again.");
    }, DEBOUNCE_MS);
  }

  const upToDate = !pending && !error && units !== "" && Number(units) === quote.units;
  return (
    <form action={action} className="space-y-5" noValidate>
      <FormFeedback state={state} />
      <TextField label="Units" name="units" inputMode="numeric" value={units} autoComplete="off"
        error={error ?? state.fieldErrors?.units} onChange={(e) => change(e.target.value)} />
      <div aria-live="polite" aria-busy={pending} className={`space-y-4 transition-opacity ${pending ? "opacity-50" : ""}`}>
        <dl className="grid grid-cols-[repeat(auto-fit,minmax(10rem,1fr))] gap-4 text-sm">
          <Figure label="You invest" value={formatMoney(quote.amount)}
            note={`${quote.units} × ${formatMoney(quote.unitPrice)}`} />
          <Figure label="Returns every month" value={formatMoney(quote.monthlyReturns)}
            note={`${formatPercent(quote.annualReturnPercent)} a year on ${formatMoney(quote.amount)}`} />
        </dl>
        <p className="border-t border-border pt-3 text-sm">
          Total returns over {yearsLabel(tenureYears)} ({quote.payouts} payouts):{" "}
          <strong className="font-display text-lg">{formatMoney(quote.totalReturns)}</strong>
        </p>
        {canBuy && overCheckoutLimit(quote.amount, checkoutLimit) && checkoutLimit && (
          <p className="text-sm text-amber-700 dark:text-amber-300">
            Online payment is limited to {formatMoney(checkoutLimit)} per payment.{" "}
            {payableOtherwise
              ? "You can pay this order by bank transfer instead."
              : `To pay online, invest up to ${formatMoney(checkoutLimit)} per order.`}
          </p>
        )}
        {quote.problems.length > 0 && (
          <ul className="space-y-1 text-sm text-amber-700 dark:text-amber-300">
            {quote.problems.map((problem) => <li key={problem}>• {problem}</li>)}
          </ul>
        )}
      </div>
      {canBuy && (
        <>
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" name="accept" required className="mt-0.5 size-4 accent-gold" />
            <span>
              I have read and accept the{" "}
              <Link href="/terms" target="_blank" rel="noopener noreferrer" className="font-medium text-gold-text hover:underline">
                terms and risk disclosure
              </Link>{" "}of this plan.
            </span>
          </label>
          <SubmitButton className="w-full" pendingLabel="Placing order…" disabled={!upToDate || !quote.valid}>
            {upToDate && quote.valid ? `Invest ${formatMoney(quote.amount)}` : "Invest"}
          </SubmitButton>
          <p className="text-xs text-muted">
            You then have 30 minutes to pay. Once your payment is confirmed, you accept your investment agreement to start
            your returns.
          </p>
        </>
      )}
    </form>
  );
}

function Figure({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1 font-display text-xl font-semibold">{value}</dd>
      <dd className="mt-0.5 text-xs text-muted">{note}</dd>
    </div>
  );
}
