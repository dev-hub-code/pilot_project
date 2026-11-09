"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { PaymentGatewayKeys } from "@/types/payment-gateway";
import type { FormState } from "@/validators/form-state";
import { savePaymentGatewayKeysAction } from "./payment-gateway-actions";

/**
 * The Razorpay API keys, webhook secret and merchant name. Saved secrets are never shown again: the fields start
 * blank, and blank keeps what is saved.
 */
export function PaymentGatewayKeysForm({ keys }: { keys: PaymentGatewayKeys }) {
  const [state, action] = useActionState<FormState, FormData>(savePaymentGatewayKeysAction, {});
  const e = state.fieldErrors ?? {};
  const v = state.values ?? {};
  const saved = (hint: string | null) => (hint ? `Saved, ending ${hint}. Leave blank to keep it.` : undefined);

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Key id" name="keyId" autoComplete="off" spellCheck={false} placeholder="rzp_live_…"
          defaultValue={v.keyId ?? keys.keyId ?? ""} error={e.keyId} />
        <TextField label="Key secret" name="keySecret" type="password" autoComplete="new-password"
          placeholder={keys.keySecretHint ? `•••• ${keys.keySecretHint}` : ""} error={e.keySecret}
          hint={keys.source === "ADMIN" ? saved(keys.keySecretHint) : "Needed for a new key id."} />
        <TextField label="Webhook secret" name="webhookSecret" type="password" autoComplete="new-password"
          placeholder={keys.webhookSecretHint ? `•••• ${keys.webhookSecretHint}` : ""} error={e.webhookSecret}
          hint={saved(keys.webhookSecretHint) ?? "The secret you set on the webhook in Razorpay."} />
        <TextField label="Name on the checkout" name="merchantName" autoComplete="off"
          defaultValue={v.merchantName ?? keys.merchantName} error={e.merchantName} />
      </div>
      <p className="text-xs text-muted">
        From the Razorpay Dashboard: Account &amp; Settings → API keys. Webhook URL: <span className="font-mono">/api/v1/webhooks/razorpay</span>{" "}
        on this site, with payment.authorized, payment.captured and virtual_account.credited. Changes apply at once.
      </p>
      <SubmitButton confirm="Change the Razorpay keys? Every payment from now on uses them. Wrong keys stop online payments.">
        Save keys
      </SubmitButton>
    </form>
  );
}
