"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { yearsLabel } from "@/features/orders/lease-years";
import { CURRENCY } from "@/lib/currency";
import { currencyLabel, formatMoney, formatPercent } from "@/utils/money";
import type { Product } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { saveProductAction } from "./investment-actions";
import { PlanPhotoInput } from "./plan-photo-form";
import { MAX_PLAN_PHOTOS } from "./plan-photos";

/**
 * An investment plan: investors buy whole containers of one type at the price per container, and choose a
 * lease of whole years within the plan's shortest and longest. Each month of it they are paid the rent % of
 * the price. The price is not paid back.
 */
export function ProductForm({ existing }: { existing?: Product }) {
  // React empties the file input after every submit, so the photo previews start afresh too (a new key each round).
  const [state, action] = useActionState<FormState & { round?: number }, FormData>(
    async (previous, formData) => ({ ...(await saveProductAction(existing?.id ?? null, previous, formData)), round: (previous.round ?? 0) + 1 }),
    {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | number | null | undefined) => state.values?.[name] ?? (fallback == null ? "" : String(fallback));
  // Live preview of what investors are paid; the backend computes the binding figures.
  const [rent, setRent] = useState(v("monthlyRentPercent", existing?.monthlyRentPercent));
  const [priceText, setPrice] = useState(v("price", existing?.price.amount));
  const [gstText, setGst] = useState(v("gstRatePercent", existing?.gstRatePercent ?? 18));
  const priceValue = Number(priceText);
  const gstValue = Number(gstText);
  const gstReady = priceValue > 0 && gstText !== "" && gstValue >= 0 && gstValue <= 28;
  const [minYearsText, setMinYears] = useState(v("minTenureYears", existing?.minTenureYears ?? 1));
  const [maxYearsText, setMaxYears] = useState(v("maxTenureYears", existing?.maxTenureYears ?? 10));
  const minYears = Number.parseInt(minYearsText, 10);
  const maxYears = Number.parseInt(maxYearsText, 10);
  const validYears = Number.isInteger(minYears) && Number.isInteger(maxYears) && minYears >= 1 && maxYears >= minYears
    && maxYears <= 30;
  const rentPercent = Number(rent);
  const ready = validYears && rentPercent > 0;
  // Per container: rent is a percentage of the price without GST, rounded to the paisa like the backend.
  const monthlyRent = priceValue > 0 ? Math.round(priceValue * rentPercent) / 100 : 0;
  const withPercent = (rupees: number, percent: number) => priceValue > 0
    ? `${formatMoney({ amount: rupees.toFixed(2), currency: CURRENCY })} (${formatPercent(percent)})`
    : `${formatPercent(percent)} of the price`;

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

      <fieldset className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <legend className="mb-3 text-sm font-medium text-muted">Price &amp; rent</legend>
        <input type="hidden" name="currency" value={CURRENCY} />
        <TextField label={currencyLabel("Price excl. GST")} name="price" inputMode="decimal"
          defaultValue={v("price", existing?.price.amount)} error={e.price} onChange={(ev) => setPrice(ev.target.value)} />
        <TextField label="GST on the price (%)" name="gstRatePercent" inputMode="decimal"
          defaultValue={v("gstRatePercent", existing?.gstRatePercent ?? 18)} error={e.gstRatePercent}
          onChange={(ev) => setGst(ev.target.value)} />
        <TextField label="Monthly rent (% of price)" name="monthlyRentPercent" inputMode="decimal"
          defaultValue={v("monthlyRentPercent", existing?.monthlyRentPercent)} error={e.monthlyRentPercent}
          onChange={(ev) => setRent(ev.target.value)} />
        <TextField label="Shortest lease (years)" name="minTenureYears" type="number" inputMode="numeric" min={1} max={30}
          defaultValue={v("minTenureYears", existing?.minTenureYears ?? 1)} error={e.minTenureYears}
          onChange={(ev) => setMinYears(ev.target.value)} />
        <TextField label="Longest lease (years)" name="maxTenureYears" type="number" inputMode="numeric" min={1} max={30}
          defaultValue={v("maxTenureYears", existing?.maxTenureYears ?? 10)} error={e.maxTenureYears}
          onChange={(ev) => setMaxYears(ev.target.value)} />
        <dl className="grid gap-2 border border-border bg-background p-4 text-sm sm:col-span-full sm:grid-cols-3">
          <div>
            <dt className="text-xs text-muted">Investor pays per container (price + GST)</dt>
            <dd className="font-medium">
              {gstReady ? formatMoney({ amount: (priceValue * (1 + gstValue / 100)).toFixed(2), currency: CURRENCY }) : "—"}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Paid to the investor every month (rent, per container)</dt>
            <dd className="font-medium">{ready ? withPercent(monthlyRent, rentPercent) : "—"}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Rent over the lease (investor&apos;s choice of years, per container)</dt>
            <dd className="font-medium">
              {!ready ? "—" : minYears === maxYears
                ? `${withPercent(monthlyRent * minYears * 12, rentPercent * minYears * 12)} over ${yearsLabel(minYears)}`
                : <>
                    {withPercent(monthlyRent * minYears * 12, rentPercent * minYears * 12)} over {yearsLabel(minYears)}
                    <span className="block">to {withPercent(monthlyRent * maxYears * 12, rentPercent * maxYears * 12)} over {yearsLabel(maxYears)}</span>
                  </>}
            </dd>
          </div>
        </dl>
      </fieldset>

      {!existing && (
        <fieldset className="space-y-2">
          <legend className="mb-3 text-sm font-medium text-muted">Photos</legend>
          <p className="text-xs text-muted">
            Shown in the marketplace as a slideshow, in the order chosen. You can add or remove photos later on the plan&apos;s page.
          </p>
          <PlanPhotoInput key={state.round ?? 0} room={MAX_PLAN_PHOTOS} error={e.photos} />
        </fieldset>
      )}

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
