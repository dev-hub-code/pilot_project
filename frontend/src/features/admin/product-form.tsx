"use client";

import Link from "next/link";
import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { CURRENCY } from "@/lib/currency";
import { currencyLabel } from "@/utils/money";
import type { Product } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { saveProductAction } from "./investment-actions";
import { PlanPhotoInput } from "./plan-photo-form";
import { MAX_PLAN_PHOTOS } from "./plan-photos";

/**
 * A container listing: investors buy whole containers of one type at the price per container, and choose a
 * lease of whole years within the listing's shortest and longest. Each month of it they are paid rent: a % of the
 * price set by the order's total under "Rent by order value". The price is not paid back. Once paid, staff assign
 * each container its number.
 */
export function ProductForm({ existing }: { existing?: Product }) {
  // React empties the file input after every submit, so the photo previews start afresh too (a new key each round).
  const [state, action] = useActionState<FormState & { round?: number }, FormData>(
    async (previous, formData) => ({ ...(await saveProductAction(existing?.id ?? null, previous, formData)), round: (previous.round ?? 0) + 1 }),
    {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | number | null | undefined) => state.values?.[name] ?? (fallback == null ? "" : String(fallback));
  return (
    <form action={action} className="space-y-8" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Container</legend>
        <SelectField label="Container type" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="Select…"
          defaultValue={v("containerType", existing?.containerType)} error={e.containerType} />
        <TextField label="Name (optional)" name="title" placeholder="Leave blank for e.g. “20ft Dry container”"
          defaultValue={v("title", existing?.title)} error={e.title} />
        <div className="sm:col-span-2">
          <TextField label="Summary" name="summary" defaultValue={v("summary", existing?.summary)} error={e.summary} />
        </div>
        <div className="sm:col-span-2">
          <TextArea label="Description" name="description" rows={5} defaultValue={v("description", existing?.description)} error={e.description} />
        </div>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <legend className="mb-3 text-sm font-medium text-muted">Price &amp; lease</legend>
        <input type="hidden" name="currency" value={CURRENCY} />
        <TextField label={currencyLabel("Price")} name="price" inputMode="decimal"
          defaultValue={v("price", existing?.price.amount)} error={e.price} />
        <TextField label="Shortest lease (years)" name="minTenureYears" type="number" inputMode="numeric" min={1} max={30}
          defaultValue={v("minTenureYears", existing?.minTenureYears ?? 1)} error={e.minTenureYears} />
        <TextField label="Longest lease (years)" name="maxTenureYears" type="number" inputMode="numeric" min={1} max={30}
          defaultValue={v("maxTenureYears", existing?.maxTenureYears ?? 10)} error={e.maxTenureYears} />
        <p className="text-xs text-muted sm:col-span-full">
          Rent is not set per listing: every order earns the monthly rent % for its total, as set under
          {" "}<Link href="/admin/products#rent-tiers" className="underline">Rent by order value</Link>.
        </p>
      </fieldset>

      {!existing && (
        <fieldset className="space-y-2">
          <legend className="mb-3 text-sm font-medium text-muted">Photos</legend>
          <p className="text-xs text-muted">
            Shown in the marketplace as a slideshow, in the order chosen. You can add or remove photos later on the listing&apos;s page.
          </p>
          <PlanPhotoInput key={state.round ?? 0} room={MAX_PLAN_PHOTOS} error={e.photos} />
        </fieldset>
      )}

      <fieldset className="grid gap-4">
        <legend className="mb-3 text-sm font-medium text-muted">Risk &amp; terms</legend>
      </fieldset>

      <SubmitButton>{existing ? "Save draft" : "Create draft"}</SubmitButton>
    </form>
  );
}
