"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { useInvestmentPlan } from "@/features/marketplace/investment-plan";
import type { FormState } from "@/validators/form-state";
import { setCartItemAction } from "./actions";

/**
 * "Invest now", under a container page's return calculator: buys exactly what the investor calculated (containers
 * and lease) and takes them to the cart to accept the terms and pay.
 */
export function InvestNowForm({ productId, inCart }: { productId: string; inCart: number }) {
  const [state, action] = useActionState<FormState, FormData>(setCartItemAction.bind(null, productId, "invest"), {});
  const plan = useInvestmentPlan();
  const problem = state.fieldErrors?.quantity ?? state.fieldErrors?.tenureYears;
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={problem ? { error: problem } : state} />
      <input type="hidden" name="quantity" value={plan?.quantity ?? ""} />
      <input type="hidden" name="tenureYears" value={plan?.years ?? ""} />
      <SubmitButton className="w-full" pendingLabel="Taking you to checkout…" disabled={plan ? !plan.ready : false}>
        Invest now
      </SubmitButton>
      <p className="text-xs text-muted">
        {inCart > 0 && `Your cart already has ${inCart} of these; investing now changes it to what you calculated. `}
        You accept the terms and check out in your cart; you then have 30 minutes to pay. Each container&apos;s number is
        assigned to you once your payment is confirmed.
      </p>
    </form>
  );
}
