"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { CURRENCY_OPTIONS } from "@/lib/countries";
import type { Product } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { saveProductAction } from "./investment-actions";

export interface ContainerOption {
  value: string;
  label: string;
}

/** ISO instant → value for <input type="datetime-local"> in UTC. */
function toLocalInput(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 16) : "";
}

export function ProductForm({ containers, existing, preselectedContainer }: {
  containers: ContainerOption[];
  existing?: Product;
  preselectedContainer?: string;
}) {
  const [state, action] = useActionState<FormState, FormData>(saveProductAction.bind(null, existing?.id ?? null), {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | number | null | undefined) => state.values?.[name] ?? (fallback == null ? "" : String(fallback));
  const [type, setType] = useState(v("investmentType", existing?.investmentType ?? "RETAIL"));

  return (
    <form action={action} className="space-y-8" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Offering</legend>
        <SelectField label="Container" name="containerId" options={containers} placeholder="Select…"
          defaultValue={v("containerId", existing?.container.id ?? preselectedContainer)} error={e.containerId} />
        <SelectField label="Investment type" name="investmentType" value={type} onChange={(ev) => setType(ev.target.value)}
          options={[{ value: "RETAIL", label: "Shared — retail investors co-own" }, { value: "HNI", label: "Standalone — one HNI investor" }]} />
        <TextField label="Title" name="title" defaultValue={v("title", existing?.title)} error={e.title} />
        <TextField label="Lessee (optional)" name="lesseeName" defaultValue={v("lesseeName", existing?.lesseeName)} error={e.lesseeName} />
        <div className="sm:col-span-2">
          <TextField label="Summary" name="summary" defaultValue={v("summary", existing?.summary)} error={e.summary} />
        </div>
        <div className="sm:col-span-2">
          <TextArea label="Description" name="description" rows={5} defaultValue={v("description", existing?.description)} error={e.description} />
        </div>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Price &amp; amounts</legend>
        <SelectField label="Currency" name="currency" options={CURRENCY_OPTIONS} defaultValue={v("currency", existing?.price.currency ?? "USD")} error={e.currency} />
        <TextField label="Container price" name="totalAmount" inputMode="decimal" defaultValue={v("totalAmount", existing?.price.amount)} error={e.totalAmount} />
        {type === "RETAIL" ? (
          <>
            <TextField label="Minimum investment" name="minimumInvestment" inputMode="decimal"
              defaultValue={v("minimumInvestment", existing?.minimumInvestment.amount)} error={e.minimumInvestment} />
            <TextField label="Increment" name="investmentIncrement" inputMode="decimal"
              defaultValue={v("investmentIncrement", existing?.investmentIncrement.amount)} error={e.investmentIncrement} />
            <TextField label="Maximum per investor (optional)" name="maximumPerInvestor" inputMode="decimal"
              defaultValue={v("maximumPerInvestor", existing?.maximumPerInvestor?.amount)} error={e.maximumPerInvestor} />
          </>
        ) : (
          <p className="self-end text-sm text-muted sm:col-span-1">Standalone containers are sold whole: minimum = price.</p>
        )}
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Rental &amp; term</legend>
        <TextField label="Expected rental per period" name="expectedRentalAmount" inputMode="decimal"
          defaultValue={v("expectedRentalAmount", existing?.expectedRentalAmount.amount)} error={e.expectedRentalAmount} />
        <SelectField label="Paid" name="rentalFrequency" defaultValue={v("rentalFrequency", existing?.rentalFrequency ?? "MONTHLY")}
          options={[{ value: "MONTHLY", label: "Monthly" }, { value: "QUARTERLY", label: "Quarterly" }]} />
        <TextField label="Term (months)" name="durationMonths" inputMode="numeric" defaultValue={v("durationMonths", existing?.durationMonths ?? 36)} error={e.durationMonths} />
        <TextField label="Offer opens (UTC, optional)" name="offerOpensAt" type="datetime-local"
          defaultValue={v("offerOpensAt", toLocalInput(existing?.offerOpensAt))} error={e.offerOpensAt} />
        <TextField label="Offer closes (UTC, optional)" name="offerClosesAt" type="datetime-local"
          defaultValue={v("offerClosesAt", toLocalInput(existing?.offerClosesAt))} error={e.offerClosesAt} />
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Risk &amp; terms</legend>
        <SelectField label="Risk level" name="riskLevel" defaultValue={v("riskLevel", existing?.riskLevel ?? "MEDIUM")}
          options={[{ value: "LOW", label: "Low" }, { value: "MEDIUM", label: "Medium" }, { value: "HIGH", label: "High" }]} />
        <TextField label="Terms version" name="termsVersion" defaultValue={v("termsVersion", existing?.termsVersion ?? "2026.1")} error={e.termsVersion} />
        <div className="sm:col-span-3">
          <TextArea label="Risk disclosure" name="riskDisclosure" rows={4} defaultValue={v("riskDisclosure", existing?.riskDisclosure)} error={e.riskDisclosure} />
        </div>
        <div className="sm:col-span-3">
          <TextArea label="Terms & conditions" name="termsAndConditions" rows={6}
            defaultValue={v("termsAndConditions", existing?.termsAndConditions)} error={e.termsAndConditions} />
        </div>
      </fieldset>

      <SubmitButton>{existing ? "Save draft" : "Create draft"}</SubmitButton>
    </form>
  );
}
