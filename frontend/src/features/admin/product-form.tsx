"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { CURRENCY } from "@/lib/currency";
import { currencyLabel, formatPercent } from "@/utils/money";
import type { Product } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { saveProductAction } from "./investment-actions";

/**
 * An investment plan: investors buy whole containers of one type at the price per container. Each
 * month of the tenure they are paid the rent % plus 100 ÷ tenure % of the price back, so the whole
 * price is returned over the lease.
 */
export function ProductForm({ existing }: { existing?: Product }) {
  const [state, action] = useActionState<FormState, FormData>(saveProductAction.bind(null, existing?.id ?? null), {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | number | null | undefined) => state.values?.[name] ?? (fallback == null ? "" : String(fallback));
  // Live preview of what investors are paid; the backend computes the binding figures.
  const [rent, setRent] = useState(v("monthlyRentPercent", existing?.monthlyRentPercent));
  const [tenure, setTenure] = useState(v("tenureMonths", existing?.tenureMonths ?? 16));
  const months = Number.parseInt(tenure, 10);
  const capital = Number.isInteger(months) && months >= 1 && months <= 120 ? 100 / months : null;
  const rentPercent = Number(rent);

  return (
    <form action={action} className="space-y-8" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Plan</legend>
        <SelectField label="Container type" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="Select…"
          defaultValue={v("containerType", existing?.containerType)} error={e.containerType} />
        <TextField label="Title" name="title" defaultValue={v("title", existing?.title)} error={e.title} />
        <div className="sm:col-span-2">
          <TextField label="Summary" name="summary" defaultValue={v("summary", existing?.summary)} error={e.summary} />
        </div>
        <div className="sm:col-span-2">
          <TextArea label="Description" name="description" rows={5} defaultValue={v("description", existing?.description)} error={e.description} />
        </div>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Price &amp; returns</legend>
        <input type="hidden" name="currency" value={CURRENCY} />
        <TextField label={currencyLabel("Price per container")} name="price" inputMode="decimal"
          defaultValue={v("price", existing?.price.amount)} error={e.price} />
        <TextField label="Monthly rent (% of price)" name="monthlyRentPercent" inputMode="decimal"
          defaultValue={v("monthlyRentPercent", existing?.monthlyRentPercent)} error={e.monthlyRentPercent}
          onChange={(ev) => setRent(ev.target.value)} />
        <TextField label="Lease tenure (months)" name="tenureMonths" type="number" inputMode="numeric" min={1} max={120}
          defaultValue={v("tenureMonths", existing?.tenureMonths ?? 16)} error={e.tenureMonths}
          onChange={(ev) => setTenure(ev.target.value)} />
        <dl className="grid gap-2 border border-border bg-background p-4 text-sm sm:col-span-3 sm:grid-cols-3">
          <div>
            <dt className="text-xs text-muted">Capital back each month (100 ÷ tenure)</dt>
            <dd className="font-medium">{capital === null ? "—" : formatPercent(capital)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Paid to the investor every month (capital + rent)</dt>
            <dd className="font-medium">{capital === null || !(rentPercent > 0) ? "—" : formatPercent(capital + rentPercent)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Over the whole lease</dt>
            <dd className="font-medium">
              {capital === null || !(rentPercent > 0) ? "—" : `${formatPercent(100, 0)} of the price back + ${formatPercent(rentPercent * months)} rent`}
            </dd>
          </div>
        </dl>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="mb-3 text-sm font-medium text-muted">Risk &amp; terms</legend>
        <TextArea label="Risk disclosure" name="riskDisclosure" rows={4} defaultValue={v("riskDisclosure", existing?.riskDisclosure)} error={e.riskDisclosure} />
        <TextArea label="Terms & conditions" name="termsAndConditions" rows={6}
          defaultValue={v("termsAndConditions", existing?.termsAndConditions)} error={e.termsAndConditions} />
      </fieldset>

      <SubmitButton>{existing ? "Save draft" : "Create draft"}</SubmitButton>
    </form>
  );
}
