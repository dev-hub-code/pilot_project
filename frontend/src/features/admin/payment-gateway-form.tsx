"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { PaymentGatewaySettings } from "@/types/payment-gateway";
import type { FormState } from "@/validators/form-state";
import { savePaymentGatewaySettingsAction } from "./payment-gateway-actions";

/** The online payments switch, the checkout limit and the checkout hold. The API keys have their own form. */
export function PaymentGatewayForm({ settings }: { settings: PaymentGatewaySettings }) {
  const [state, action] = useActionState<FormState, FormData>(savePaymentGatewaySettingsAction, {});
  const e = state.fieldErrors ?? {};
  const v = state.values ?? {};
  const max = Number(settings.maxAmount.amount);

  return (
    <form action={action} className="space-y-5" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-2">
        <legend className="mb-2 text-sm font-medium">Ways online investors pay</legend>
        <Switch name="onlinePayments" defaultChecked={settings.onlinePayments} title="Online payments through Razorpay"
          hint="Off: online investors pay by bank transfer to a company account, or from their available balance." />
      </fieldset>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Checkout limit per payment (₹)" name="maxAmount" inputMode="decimal" autoComplete="off"
          defaultValue={v.maxAmount ?? (max > 0 ? String(max) : "0")} error={e.maxAmount} />
        <TextField label="Checkout hold (minutes)" name="checkoutWindowMinutes" inputMode="numeric" autoComplete="off"
          defaultValue={v.checkoutWindowMinutes ?? String(settings.checkoutWindowMinutes)} error={e.checkoutWindowMinutes} />
      </div>
      <p className="text-xs text-muted">
        Set the checkout limit to what Razorpay allows on your account (0 for no limit); larger orders are paid another
        way. The checkout hold is how long an order stays reserved while the investor pays online.
      </p>
      <SubmitButton
        confirm="Change how online investors pay? Payments already started are not affected.">Save</SubmitButton>
    </form>
  );
}

/** A checkbox drawn as an on/off switch, in a row with its title and hint. */
function Switch({ name, defaultChecked, title, hint }: { name: string; defaultChecked: boolean; title: string; hint: string }) {
  return (
    <label className="flex cursor-pointer items-start gap-4 rounded-xl border border-border bg-background/40 px-4 py-3 text-sm transition-colors hover:border-gold/50 has-[:checked]:border-gold/60 has-[:checked]:bg-gold/5 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-gold/40">
      <span className="min-w-0 flex-1">
        <span className="font-medium">{title}</span>
        <span className="block text-xs text-muted">{hint}</span>
      </span>
      <input type="checkbox" name={name} defaultChecked={defaultChecked} className="peer sr-only" />
      <span aria-hidden="true"
        className="relative mt-0.5 h-6 w-11 shrink-0 rounded-full bg-border transition-colors peer-checked:bg-gold after:absolute after:top-0.5 after:left-0.5 after:size-5 after:rounded-full after:bg-surface after:shadow-sm after:transition-transform peer-checked:after:translate-x-5" />
    </label>
  );
}
