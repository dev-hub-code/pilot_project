"use client";

import { useActionState } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { removeCartItemAction, setCartItemAction } from "./actions";

export function CartLineForm({ productId, amount, currency }: { productId: string; amount: string; currency: string }) {
  const [state, action] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "stay"), {});
  const error = state.fieldErrors?.amount;
  return (
    <div className="space-y-2">
      <FormFeedback state={state} />
      <div className="flex flex-wrap items-end gap-2">
        <form action={action} className="flex items-end gap-2" noValidate>
          <label className="space-y-1.5">
            <span className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">Amount ({currency})</span>
            <input name="amount" inputMode="decimal" defaultValue={state.values?.amount ?? amount} aria-invalid={error ? true : undefined}
              className="h-11 w-40 rounded-none border border-border bg-surface px-3 text-sm tabular-nums outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
          </label>
          <SubmitButton variant="quiet" pendingLabel="Saving…">Update</SubmitButton>
        </form>
        <form action={removeCartItemAction.bind(null, productId)}>
          <Button type="submit" variant="quiet">Remove</Button>
        </form>
      </div>
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}
