"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { checkoutAction } from "./actions";

export interface TermsToAccept {
  productId: string;
  code: string;
  title: string;
  termsVersion: string;
}

/** The investor accepts each offering's terms (by version) and places the order. */
export function CheckoutForm({ idempotencyKey, terms, total, disabled }: {
  idempotencyKey: string;
  terms: TermsToAccept[];
  total: string;
  disabled: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(checkoutAction.bind(null, idempotencyKey), {});
  return (
    <form action={action} className="space-y-5">
      <FormFeedback state={state} />
      <input type="hidden" name="lineCount" value={terms.length} />
      <fieldset className="space-y-3">
        <legend className="text-xs font-medium uppercase tracking-[0.08em] text-muted">Terms</legend>
        {terms.map((t) => (
          <label key={t.productId} className="flex items-start gap-3 text-sm">
            <input type="checkbox" name="accept" value={`${t.productId}|${t.termsVersion}`} required
              className="mt-0.5 size-4 accent-gold" />
            <span>
              I have read and accept the terms (version {t.termsVersion}) and risk disclosure of{" "}
              <a href={`/marketplace/${t.productId}`} target="_blank" rel="noopener noreferrer" className="font-medium text-gold-text hover:underline">
                {t.code}
              </a>.
            </span>
          </label>
        ))}
      </fieldset>
      <SubmitButton className="w-full" pendingLabel="Placing order…">
        {disabled ? "Resolve the issues above" : `Place order · ${total}`}
      </SubmitButton>
      <p className="text-xs text-muted">
        Placing the order reserves your share for 30 minutes while you pay. Your investment is confirmed once payment arrives.
      </p>
    </form>
  );
}
