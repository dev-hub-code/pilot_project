"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import type { FormState } from "@/validators/form-state";
import { cancelWithdrawalAction, respondWithdrawalAction } from "./actions";

export function CancelWithdrawalForm({ withdrawalId }: { withdrawalId: string }) {
  const [state, action] = useActionState<FormState, FormData>(cancelWithdrawalAction.bind(null, withdrawalId), {});
  return (
    <form action={action} className="space-y-2">
      <FormFeedback state={state} />
      <SubmitButton variant="danger" confirm="Cancel this withdrawal? The amount goes back to your wallet.">Cancel withdrawal</SubmitButton>
    </form>
  );
}

export function RespondWithdrawalForm({ withdrawalId }: { withdrawalId: string }) {
  const [state, action] = useActionState<FormState, FormData>(respondWithdrawalAction.bind(null, withdrawalId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextArea label="Your answer" name="message" rows={3} maxLength={1000} required
        defaultValue={state.values?.message} error={state.fieldErrors?.message} />
      <SubmitButton pendingLabel="Sending…">Send answer</SubmitButton>
    </form>
  );
}
