"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { WithdrawalPayoutMethod } from "@/types/withdrawal";
import type { FormState } from "@/validators/form-state";
import { chooseReferralModeAction } from "./actions";

/** The once-a-year choice of how referral earnings are paid: into the bank account or in cash at the office. */
export function ReferralModeForm({ year, current, bankAvailable }: {
  year: number;
  /** The default until the investor chooses. */
  current: WithdrawalPayoutMethod;
  /** Bank needs a verified bank account. */
  bankAvailable: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(chooseReferralModeAction, {});
  const [mode, setMode] = useState<WithdrawalPayoutMethod>(bankAvailable ? current : "OFFICE_CASH");
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-2">
        <legend className="sr-only">How referral earnings are paid in {year}</legend>
        <label className={`flex items-start gap-2 text-sm ${bankAvailable ? "" : "text-muted"}`}>
          <input type="radio" name="mode" value="BANK_TRANSFER" checked={mode === "BANK_TRANSFER"} disabled={!bankAvailable}
            onChange={() => setMode("BANK_TRANSFER")} className="mt-1" />
          <span>Into my bank account{!bankAvailable && " (add and verify a bank account first)"}</span>
        </label>
        <label className="flex items-start gap-2 text-sm">
          <input type="radio" name="mode" value="OFFICE_CASH" checked={mode === "OFFICE_CASH"}
            onChange={() => setMode("OFFICE_CASH")} className="mt-1" />
          <span>Cash</span>
        </label>
      </fieldset>
      <SubmitButton confirm={`You can choose only once in ${year}. Save this choice?`}>Save my choice for {year}</SubmitButton>
    </form>
  );
}
