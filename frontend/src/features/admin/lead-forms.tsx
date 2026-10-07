"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { INTEREST_LABEL, STAGES, STAGE_LABEL } from "@/features/leads/labels";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import type { Assignee, Lead, LeadStage } from "@/types/lead";
import type { FormState } from "@/validators/form-state";
import { assignLeadAction, changeStageAction, logActivityAction, saveLeadAction } from "./lead-actions";

const CURRENCIES = ["USD", "EUR", "GBP", "SGD", "AED"].map((c) => ({ value: c, label: c }));

/** "2026-10-07T09:30:00Z" → "2026-10-07T09:30" for a datetime-local input (UTC). */
function toLocalInput(iso: string | null | undefined): string | undefined {
  return iso ? iso.slice(0, 16) : undefined;
}

export function LeadForm({ lead, assignees }: { lead?: Lead; assignees?: Assignee[] }) {
  const [state, action] = useActionState<FormState, FormData>(saveLeadAction.bind(null, lead?.id ?? null), {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback?: string | null) => state.values?.[name] ?? fallback ?? undefined;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" defaultValue={v("firstName", lead?.firstName)} error={e.firstName} />
        <TextField label="Last name" name="lastName" defaultValue={v("lastName", lead?.lastName)} error={e.lastName} />
        <TextField label="Email" name="email" type="email" defaultValue={v("email", lead?.email)} error={e.email} />
        <TextField label="Phone" name="phone" type="tel" defaultValue={v("phone", lead?.phone)} error={e.phone} />
        <SelectField label="Country" name="country" defaultValue={v("country", lead?.country) ?? ""} placeholder="—" options={COUNTRY_OPTIONS} />
        <SelectField label="Interested in" name="interest" defaultValue={v("interest", lead?.interest ?? "UNSURE")}
          options={Object.entries(INTEREST_LABEL).map(([value, label]) => ({ value, label }))} />
        <TextField label="Estimated investment" name="estimatedAmount" inputMode="decimal"
          defaultValue={v("estimatedAmount", lead?.estimate?.amount)} error={e.estimatedAmount} />
        <SelectField label="Currency" name="estimatedCurrency" defaultValue={v("estimatedCurrency", lead?.estimate?.currency) ?? ""}
          placeholder="—" options={CURRENCIES} />
        <TextField label="Next follow-up (UTC)" name="nextFollowUpAt" type="datetime-local"
          defaultValue={v("nextFollowUpAt", toLocalInput(lead?.nextFollowUpAt))} error={e.nextFollowUpAt} />
        {!lead && assignees && (
          <SelectField label="Owner" name="ownerId" defaultValue={v("ownerId") ?? ""} placeholder="Unassigned"
            options={assignees.map((a) => ({ value: a.userId, label: a.name }))} />
        )}
      </div>
      <SubmitButton>{lead ? "Save changes" : "Create lead"}</SubmitButton>
    </form>
  );
}

export function StageForm({ leadId, current }: { leadId: string; current: LeadStage }) {
  const [state, action] = useActionState<FormState, FormData>(changeStageAction.bind(null, leadId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label="Move to" name="stage" defaultValue={state.values?.stage ?? STAGES.find((s) => s !== current)}
        options={STAGES.filter((s) => s !== current).map((s) => ({ value: s, label: STAGE_LABEL[s] }))} />
      <TextField label="Reason (required for lost)" name="reason" defaultValue={state.values?.reason} error={state.fieldErrors?.reason} />
      <SubmitButton variant="secondary">Update stage</SubmitButton>
    </form>
  );
}

export function ActivityForm({ leadId }: { leadId: string }) {
  const [state, action] = useActionState<FormState, FormData>(logActivityAction.bind(null, leadId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label="Type" name="type" defaultValue={state.values?.type ?? "CALL"}
        options={[{ value: "CALL", label: "Call" }, { value: "EMAIL", label: "Email" }, { value: "MEETING", label: "Meeting" }, { value: "NOTE", label: "Note" }]} />
      <div className="space-y-1.5">
        <label htmlFor="activity-body" className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">What happened</label>
        <textarea id="activity-body" name="body" rows={3} maxLength={4000} defaultValue={state.values?.body}
          className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        {state.fieldErrors?.body && <p className="text-xs text-rose-600 dark:text-rose-400">{state.fieldErrors.body}</p>}
      </div>
      <SubmitButton variant="secondary">Log activity</SubmitButton>
    </form>
  );
}

export function AssignForm({ leadId, ownerId, assignees }: { leadId: string; ownerId: string | null; assignees: Assignee[] }) {
  const [state, action] = useActionState<FormState, FormData>(assignLeadAction.bind(null, leadId), {});
  return (
    <form action={action} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label="Owner" name="ownerId" defaultValue={ownerId ?? ""} placeholder="Unassigned"
        options={assignees.map((a) => ({ value: a.userId, label: `${a.name} · ${a.email}` }))} />
      <SubmitButton variant="secondary">Assign</SubmitButton>
    </form>
  );
}
