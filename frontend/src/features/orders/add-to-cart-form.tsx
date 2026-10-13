"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { setCartItemAction } from "./actions";
import { leaseOptions } from "./lease-years";

export function AddToCartForm({ productId, defaultQuantity, defaultYears, minYears, maxYears, inCart }: {
  productId: string;
  defaultQuantity: number;
  /** The lease the investor chooses, in whole years from {@code minYears} to {@code maxYears}. */
  defaultYears: number;
  minYears: number;
  maxYears: number;
  /** The plan is already in the cart: submitting changes how many containers and the lease. */
  inCart: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "cart"), {});
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField id="invest-quantity" label="Containers" name="quantity" type="number" inputMode="numeric" min={1}
        max={50} defaultValue={state.values?.quantity ?? String(defaultQuantity)}
        error={state.fieldErrors?.quantity} />
      <SelectField id="invest-years" label="Lease tenure" name="tenureYears" options={leaseOptions(minYears, maxYears)}
        defaultValue={state.values?.tenureYears ?? String(defaultYears)} error={state.fieldErrors?.tenureYears} />
      <SubmitButton className="w-full" pendingLabel="Adding…">{inCart ? "Update cart" : "Add to cart"}</SubmitButton>
      <p className="text-xs text-muted">
        Containers are reserved when you check out; you then have 30 minutes to pay. Each container&apos;s number is
        assigned to you once your payment is confirmed.
      </p>
    </form>
  );
}
