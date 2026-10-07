"use client";

import { useActionState, useState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import type { FormState } from "@/validators/form-state";
import { ACCEPTED_FILE_TYPES } from "@/validators/profile";
import { submitKycAction } from "./actions";

const DOCUMENT_TYPES = [
  { value: "PASSPORT", label: "Passport" },
  { value: "NATIONAL_ID", label: "National ID card" },
  { value: "DRIVING_LICENSE", label: "Driving licence" },
] as const;

const FILE_HINT = "PDF, JPEG or PNG, up to 5 MB";

export function KycForm() {
  const [state, action] = useActionState<FormState, FormData>(submitKycAction, {});
  const [documentType, setDocumentType] = useState(state.values?.documentType ?? "PASSPORT");
  const e = state.fieldErrors ?? {};
  const v = (name: string) => state.values?.[name] ?? "";
  const needsBack = documentType !== "PASSPORT";

  return (
    <form action={action} className="space-y-6" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Your details, exactly as on the document</legend>
        <TextField label="Legal first name(s)" name="legalFirstName" defaultValue={v("legalFirstName")} error={e.legalFirstName} />
        <TextField label="Legal last name" name="legalLastName" defaultValue={v("legalLastName")} error={e.legalLastName} />
        <TextField label="Date of birth" name="dateOfBirth" type="date" defaultValue={v("dateOfBirth")} error={e.dateOfBirth} />
        <SelectField label="Nationality" name="nationality" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("nationality")} error={e.nationality} />
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Identity document</legend>
        <SelectField label="Document type" name="documentType" options={DOCUMENT_TYPES} value={documentType}
          onChange={(event) => setDocumentType(event.target.value)} error={e.documentType} />
        <TextField label="Document number" name="documentNumber" autoComplete="off" error={e.documentNumber} />
        <SelectField label="Issuing country" name="documentIssuingCountry" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("documentIssuingCountry")} error={e.documentIssuingCountry} />
        <TextField label="Expiry date" name="documentExpiryDate" type="date" defaultValue={v("documentExpiryDate")}
          error={e.documentExpiryDate} />
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Uploads (re-select files after an error)</legend>
        <FileField label="Document — front" name="identityFront" accept={ACCEPTED_FILE_TYPES} hint={FILE_HINT}
          error={e.identityFront} required />
        <FileField label="Document — back" name="identityBack" accept={ACCEPTED_FILE_TYPES} hint={FILE_HINT}
          error={e.identityBack} required={needsBack} />
        <FileField label="Selfie holding the document" name="selfie" accept={ACCEPTED_FILE_TYPES} hint={FILE_HINT}
          error={e.selfie} required />
        <FileField label="Proof of address" name="proofOfAddress" accept={ACCEPTED_FILE_TYPES}
          hint="Utility bill or bank statement from the last 3 months" error={e.proofOfAddress} />
      </fieldset>

      <p className="text-xs text-muted">
        Your documents are encrypted and only visible to our verification team. Every access is logged.
      </p>
      <SubmitButton pendingLabel="Uploading…">Submit for verification</SubmitButton>
    </form>
  );
}
