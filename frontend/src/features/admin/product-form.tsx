"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { AUDIENCE_OPTIONS } from "@/features/plans/labels";
import { CURRENCY } from "@/lib/currency";
import type { Product } from "@/types/plan";
import type { FormState } from "@/validators/form-state";
import { saveProductAction } from "./investment-actions";

/**
 * A plan: one tenure for one investor type, with its annual return and the fewest units an investor may buy. The investment is not paid back.
 */
export function ProductForm({ existing }: { existing?: Product }) {
  const [state, action] = useActionState<FormState, FormData>(saveProductAction.bind(null, existing?.id ?? null), {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | number | null | undefined) => state.values?.[name] ?? (fallback == null ? "" : String(fallback));
  return (
    <form action={action} className="space-y-8" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Plan</legend>
        <SelectField label="Offered to" name="audience" options={AUDIENCE_OPTIONS} placeholder="Select…"
          defaultValue={v("audience", existing?.audience)} error={e.audience} />
        <TextField label="Name" name="title"
          defaultValue={v("title", existing?.title)} error={e.title} />
        <div className="sm:col-span-2">
          <TextField label="Summary" name="summary" defaultValue={v("summary", existing?.summary)} error={e.summary} />
        </div>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Tenure, returns and units</legend>
        <input type="hidden" name="currency" value={CURRENCY} />
        <TextField label="Tenure (years)" name="tenureYears" type="number" inputMode="numeric" min={1} max={30}
          defaultValue={v("tenureYears", existing?.tenureYears ?? 3)} error={e.tenureYears} />
        <TextField label="Annual return (%)" name="annualReturnPercent" inputMode="decimal"
          defaultValue={v("annualReturnPercent", existing?.annualReturnPercent)} error={e.annualReturnPercent} />
        <TextField label="Minimum units" name="minUnits" type="number" inputMode="numeric" min={1}
          defaultValue={v("minUnits", existing?.minUnits ?? 1)} error={e.minUnits} />
        <p className="text-xs text-muted sm:col-span-full">
          Investors earn the annual return on what they invested, paid monthly for the tenure. One unit costs ₹500 on every plan. These terms are fixed
          once the plan is published, and each order keeps the terms it was placed on.
        </p>
      </fieldset>

      <SubmitButton>{existing ? "Save draft" : "Create draft"}</SubmitButton>
    </form>
  );
}
