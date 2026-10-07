"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { CONDITION_LABEL, CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { COUNTRY_OPTIONS, CURRENCY_OPTIONS } from "@/lib/countries";
import type { ContainerDetail } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { saveContainerAction } from "./investment-actions";

const CONDITIONS = Object.entries(CONDITION_LABEL).map(([value, label]) => ({ value, label }));

export function ContainerForm({ existing }: { existing?: ContainerDetail }) {
  const [state, action] = useActionState<FormState, FormData>(saveContainerAction.bind(null, existing?.container.id ?? null), {});
  const e = state.fieldErrors ?? {};
  const c = existing?.container;
  const v = (name: string, fallback: string | number | null | undefined) => state.values?.[name] ?? (fallback == null ? "" : String(fallback));

  return (
    <form action={action} className="space-y-6" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Identity {c && "(fixed once registered)"}</legend>
        <TextField label="Container number (ISO 6346)" name="containerNumber" placeholder="SLSU 123456 7"
          defaultValue={v("containerNumber", c?.containerNumber)} readOnly={!!c} error={e.containerNumber} />
        <SelectField label="Type" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="Select…"
          defaultValue={v("containerType", c?.containerType)} error={e.containerType} disabled={!!c} />
        {c && <input type="hidden" name="containerType" value={c.containerType} />}
        <SelectField label="Condition" name="condition" options={CONDITIONS} placeholder="Select…"
          defaultValue={v("condition", c?.condition)} error={e.condition} />
      </fieldset>
      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Specification</legend>
        <TextField label="Capacity (m³)" name="capacityCbm" inputMode="decimal" defaultValue={v("capacityCbm", c?.capacityCbm)} error={e.capacityCbm} />
        <TextField label="Max gross (kg)" name="maxGrossKg" inputMode="numeric" defaultValue={v("maxGrossKg", c?.maxGrossKg)} error={e.maxGrossKg} />
        <TextField label="Tare (kg)" name="tareKg" inputMode="numeric" defaultValue={v("tareKg", c?.tareKg)} error={e.tareKg} />
        <TextField label="Year built" name="manufactureYear" inputMode="numeric" defaultValue={v("manufactureYear", c?.manufactureYear)} error={e.manufactureYear} />
        <TextField label="Manufacturer" name="manufacturer" defaultValue={v("manufacturer", c?.manufacturer)} error={e.manufacturer} />
      </fieldset>
      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Location &amp; cost (cost is never shown to investors)</legend>
        <TextField label="Current location" name="currentLocation" placeholder="Port of Rotterdam, Depot 4"
          defaultValue={v("currentLocation", c?.currentLocation)} error={e.currentLocation} />
        <SelectField label="Country" name="locationCountry" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("locationCountry", c?.locationCountry)} error={e.locationCountry} />
        <div className="grid grid-cols-[1fr_7rem] gap-2">
          <TextField label="Acquisition cost" name="acquisitionCost" inputMode="decimal"
            defaultValue={v("acquisitionCost", existing?.acquisitionCost)} error={e.acquisitionCost} />
          <SelectField label="Currency" name="acquisitionCurrency" options={CURRENCY_OPTIONS} placeholder="—"
            defaultValue={v("acquisitionCurrency", existing?.acquisitionCurrency)} error={e.acquisitionCurrency} />
        </div>
      </fieldset>
      <TextArea label="Internal notes" name="notes" rows={2} defaultValue={v("notes", existing?.notes)} error={e.notes} />
      <SubmitButton>{c ? "Save changes" : "Register container"}</SubmitButton>
    </form>
  );
}
