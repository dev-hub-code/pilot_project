"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { setCartItemAction } from "./actions";

export function AddToCartForm({ productId, defaultQuantity, available, inCart }: {
  productId: string;
  defaultQuantity: number;
  available: number;
  /** The plan is already in the cart: submitting changes how many containers. */
  inCart: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "cart"), {});
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField id="invest-quantity" label="Containers" name="quantity" type="number" inputMode="numeric" min={1}
        max={Math.max(1, Math.min(50, available))} defaultValue={state.values?.quantity ?? String(defaultQuantity)}
        error={state.fieldErrors?.quantity} />
      <SubmitButton className="w-full" pendingLabel="Adding…">{inCart ? "Update containers in cart" : "Add to cart"}</SubmitButton>
      <p className="text-xs text-muted">
        Containers are reserved when you check out; you then have 30 minutes to pay. Each container&apos;s number is
        assigned to you once your payment is confirmed.
      </p>
    </form>
  );
}
