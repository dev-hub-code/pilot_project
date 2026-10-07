"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { updateNotificationsAction } from "./actions";

export function NotificationsForm({ email, sms }: { email: boolean; sms: boolean }) {
  const [state, action] = useActionState<FormState, FormData>(updateNotificationsAction, {});
  return (
    <form action={action} className="space-y-5">
      <FormFeedback state={state} />
      <fieldset className="space-y-3">
        <legend className="mb-1 text-sm font-medium">Tell me about investments, earnings and withdrawals by</legend>
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" name="emailNotifications" defaultChecked={email} className="mt-0.5 size-4" />
          <span>
            <span className="block">Email</span>
            <span className="block text-xs text-muted">Payment confirmations, monthly payouts, withdrawals and support replies.</span>
          </span>
        </label>
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" name="smsNotifications" defaultChecked={sms} className="mt-0.5 size-4" />
          <span>
            <span className="block">SMS</span>
            <span className="block text-xs text-muted">Short alerts to the phone number on your profile.</span>
          </span>
        </label>
      </fieldset>
      <p className="text-xs text-muted">In-app notifications (the bell) are always on.</p>
      <SubmitButton>Save preferences</SubmitButton>
    </form>
  );
}
