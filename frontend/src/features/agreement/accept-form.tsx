"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { acceptAgreementAction } from "./actions";

/** "I have read and accept" and the button that starts the lease. */
export function AcceptAgreementForm({ holdingId, agreementNumber }: { holdingId: string; agreementNumber: string }) {
  const [state, action] = useActionState<FormState, FormData>(acceptAgreementAction.bind(null, holdingId), {});
  const error = state.fieldErrors?.agree;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <label className="flex items-start gap-3 text-sm">
        <input type="checkbox" name="agree" className="mt-1 size-4 accent-gold"
          aria-invalid={error ? true : undefined} aria-describedby={error ? "agree-error" : undefined} />
        <span>
          I have read lease agreement <span className="font-mono">{agreementNumber}</span> and accept it. I understand my
          lease and rent start at the next full hour after I accept.
        </span>
      </label>
      {error && <p id="agree-error" className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
      <SubmitButton pendingLabel="Accepting…">Accept and start my lease</SubmitButton>
    </form>
  );
}
