"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { ActionButton } from "./action-button";
import { removeCartItemAction, setCartItemAction } from "./actions";
import { leaseOptions } from "./lease-years";

/** Containers and lease of one cart line; the lease is chosen in whole years within the plan's limits. */
export function CartLineForm({ productId, title, quantity, tenureYears, minYears, maxYears }: {
  productId: string;
  /** The plan, named when asking to remove it. */
  title: string;
  quantity: number;
  tenureYears: number;
  minYears: number;
  maxYears: number;
}) {
  const [state, action] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "stay"), {});
  const error = state.fieldErrors?.quantity ?? state.fieldErrors?.tenureYears;
  return (
    <div className="space-y-2">
      <FormFeedback state={state} />
      <div className="flex flex-wrap items-end gap-2">
        <form action={action} className="flex flex-wrap items-end gap-2" noValidate>
          <label className="space-y-1.5">
            <span className="block text-sm font-medium text-foreground">Containers</span>
            <input name="quantity" type="number" inputMode="numeric" min={1} max={50}
              defaultValue={state.values?.quantity ?? String(quantity)} aria-invalid={error ? true : undefined}
              className="h-11 w-24 rounded-none border border-border bg-surface px-3 text-sm tabular-nums outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
          </label>
          {minYears > 0 && (
            <label className="space-y-1.5">
              <span className="block text-sm font-medium text-foreground">Lease</span>
              <select name="tenureYears" defaultValue={state.values?.tenureYears ?? String(tenureYears)}
                className="h-11 rounded-none border border-border bg-surface px-3 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold">
                {leaseOptions(minYears, maxYears).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </label>
          )}
          <SubmitButton variant="quiet" pendingLabel="Saving…">Update</SubmitButton>
        </form>
        <ActionButton action={removeCartItemAction.bind(null, productId)} label="Remove" variant="quiet"
          pendingLabel="Removing…" confirm={`Remove ${title} from your cart?`} />
      </div>
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}
