"use client";

import { startTransition, useActionState, useEffect, useRef, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import type { FormState } from "@/validators/form-state";
import { ActionButton } from "./action-button";
import { removeCartItemAction, setCartItemAction } from "./actions";
import { leaseOptions } from "./lease-years";

/** How long typing may pause before the new number of containers is saved. */
const SAVE_DELAY_MS = 600;
const CONTAINERS = /^\d{1,2}$/;

/**
 * Containers and lease of one cart line, saved as they change (no Update button): the lease at once, the number of
 * containers when typing pauses or on Enter. The lease is chosen in whole years within the plan's limits.
 */
export function CartLineForm({ productId, title, quantity, tenureYears, minYears, maxYears }: {
  productId: string;
  /** The plan, named when asking to remove it. */
  title: string;
  quantity: number;
  tenureYears: number;
  minYears: number;
  maxYears: number;
}) {
  const [state, action, saving] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "stay"), {});
  const form = useRef<HTMLFormElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const [invalid, setInvalid] = useState<string | null>(null);
  // Remembers what was last sent, so an unchanged form is not saved again.
  const sent = useRef(`${quantity}/${tenureYears}`);
  useEffect(() => () => clearTimeout(timer.current), []);
  // A save that failed was not kept: the same values may be sent again.
  useEffect(() => {
    if (state.error || state.fieldErrors) sent.current = "";
  }, [state]);

  function save(delay: number) {
    clearTimeout(timer.current);
    const element = form.current;
    if (!element) return;
    const data = new FormData(element);
    const count = String(data.get("quantity") ?? "").trim();
    if (count === "") {
      setInvalid(null); // still typing
      return;
    }
    if (!CONTAINERS.test(count) || Number(count) < 1 || Number(count) > 50) {
      setInvalid("Enter 1 to 50 containers.");
      return;
    }
    setInvalid(null);
    const key = `${Number(count)}/${String(data.get("tenureYears") ?? tenureYears)}`;
    if (key === sent.current) return;
    timer.current = setTimeout(() => {
      sent.current = key;
      // Dispatched directly, not as a form submission: a submitted form is reset afterwards, which would undo typing.
      startTransition(() => action(data));
    }, delay);
  }

  const error = invalid ?? state.fieldErrors?.quantity ?? state.fieldErrors?.tenureYears;
  return (
    <div className="space-y-2">
      {state.error && <FormFeedback state={{ error: state.error }} />}
      <div className="flex flex-wrap items-end gap-2">
        <form ref={form} className="flex flex-wrap items-end gap-2" noValidate
          onSubmit={(event) => {
            event.preventDefault(); // Enter: save now
            save(0);
          }}>
          <label className="space-y-1.5">
            <span className="block text-sm font-medium text-foreground">Containers</span>
            <input name="quantity" type="number" inputMode="numeric" min={1} max={50}
              defaultValue={String(quantity)} aria-invalid={error ? true : undefined} onChange={() => save(SAVE_DELAY_MS)}
              className="h-11 w-24 rounded-none border border-border bg-surface px-3 text-sm tabular-nums outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
          </label>
          {minYears > 0 && (
            <label className="space-y-1.5">
              <span className="block text-sm font-medium text-foreground">Lease</span>
              <select name="tenureYears" defaultValue={String(tenureYears)} onChange={() => save(0)}
                className="h-11 rounded-none border border-border bg-surface px-3 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold">
                {leaseOptions(minYears, maxYears).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </label>
          )}
        </form>
        <ActionButton action={removeCartItemAction.bind(null, productId)} label="Remove" variant="quiet"
          pendingLabel="Removing…" confirm={`Remove ${title} from your cart?`} />
      </div>
      <p aria-live="polite" className={`min-h-4 text-xs ${error ? "text-rose-600 dark:text-rose-400" : "text-muted"}`}>
        {error ?? (saving ? "Saving…" : state.success ? "Saved" : "")}
      </p>
    </div>
  );
}
