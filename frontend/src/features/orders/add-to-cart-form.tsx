"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { setCartItemAction } from "./actions";

export function AddToCartForm({ productId, currency, defaultAmount, inCart }: {
  productId: string;
  currency: string;
  defaultAmount: string;
  /** The offering is already in the cart: submitting changes its amount. */
  inCart: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "cart"), {});
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField id="invest-amount" label={`Investment (${currency})`} name="amount" inputMode="decimal"
        defaultValue={state.values?.amount ?? defaultAmount} error={state.fieldErrors?.amount} />
      <SubmitButton className="w-full" pendingLabel="Adding…">{inCart ? "Update amount in cart" : "Add to cart"}</SubmitButton>
      <p className="text-xs text-muted">
        Nothing is reserved until you check out. You then have 30 minutes to pay.
      </p>
    </form>
  );
}
