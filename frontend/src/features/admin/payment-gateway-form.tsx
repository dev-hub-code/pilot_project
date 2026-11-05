"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { PaymentGatewaySettings } from "@/types/payment-gateway";
import type { FormState } from "@/validators/form-state";
import { savePaymentGatewaySettingsAction } from "./payment-gateway-actions";

/** The switches, the checkout limit and the payment windows. The API keys are not settable here. */
export function PaymentGatewayForm({ settings }: { settings: PaymentGatewaySettings }) {
  const [state, action] = useActionState<FormState, FormData>(savePaymentGatewaySettingsAction, {});
  const e = state.fieldErrors ?? {};
  const v = state.values ?? {};
  const max = Number(settings.maxAmount.amount);

  return (
    <form action={action} className="space-y-5" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-3">
        <legend className="text-sm font-medium">Ways online investors pay</legend>
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" name="onlinePayments" defaultChecked={settings.onlinePayments} className="mt-0.5 size-4 accent-gold" />
          <span>
            <span className="font-medium">Online payments through Razorpay</span>
            <span className="block text-xs text-muted">Off: online investors pay by bank transfer to a company account, or from their available balance.</span>
          </span>
        </label>
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" name="virtualAccounts" defaultChecked={settings.virtualAccounts} className="mt-0.5 size-4 accent-gold" />
          <span>
            <span className="font-medium">Bank transfer into a Razorpay virtual account</span>
            <span className="block text-xs text-muted">Needs Smart Collect activated on the Razorpay account. Leave off until Razorpay confirms it.</span>
          </span>
        </label>
      </fieldset>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label="Checkout limit per payment (₹)" name="maxAmount" inputMode="decimal" autoComplete="off"
          defaultValue={v.maxAmount ?? (max > 0 ? String(max) : "0")} error={e.maxAmount} />
        <TextField label="Checkout hold (minutes)" name="checkoutWindowMinutes" inputMode="numeric" autoComplete="off"
          defaultValue={v.checkoutWindowMinutes ?? String(settings.checkoutWindowMinutes)} error={e.checkoutWindowMinutes} />
        <TextField label="Bank transfer hold (hours)" name="virtualAccountWindowHours" inputMode="numeric" autoComplete="off"
          defaultValue={v.virtualAccountWindowHours ?? String(settings.virtualAccountWindowHours)} error={e.virtualAccountWindowHours} />
      </div>
      <p className="text-xs text-muted">
        Set the checkout limit to what Razorpay allows on your account (0 for no limit); larger orders are paid another
        way. The holds are how long an order stays reserved while the investor pays.
      </p>
      <SubmitButton variant="secondary"
        confirm="Change how online investors pay? Payments already started are not affected.">Save</SubmitButton>
    </form>
  );
}
