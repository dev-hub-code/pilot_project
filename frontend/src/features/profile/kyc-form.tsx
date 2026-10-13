"use client";

import { useActionState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { upperCase } from "@/utils/input";
import type { FormState } from "@/validators/form-state";
import { ACCEPTED_FILE_TYPES } from "@/validators/profile";
import { submitKycDocumentAction } from "./actions";

/**
 * One identity document, Aadhaar or PAN, with its number. They can be uploaded at different times; the two go for
 * review once both are in. Name, date of birth and nationality are taken from the profile.
 */
export function KycDocumentForm({ document, replacing }: { document: "AADHAAR" | "PAN"; replacing?: boolean }) {
  const [state, action] = useActionState<FormState, FormData>(submitKycDocumentAction.bind(null, document), {});
  const e = state.fieldErrors ?? {};
  const aadhaar = document === "AADHAAR";

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      {aadhaar ? (
        <>
          <TextField label="Aadhaar number" name="aadhaarNumber" inputMode="numeric" autoComplete="off"
            placeholder="1234 5678 9012" maxLength={14} error={e.aadhaarNumber} />
          <FileField label="Aadhaar card" name="aadhaarCard" accept={ACCEPTED_FILE_TYPES} required error={e.aadhaarCard}
            hint="One file with both sides, e.g. your e-Aadhaar PDF. PDF, JPEG or PNG, up to 5 MB" />
        </>
      ) : (
        <>
          <TextField label="PAN" name="panNumber" autoComplete="off" placeholder="ABCDE1234F" maxLength={10}
            autoCapitalize="characters" spellCheck={false} onChange={upperCase} error={e.panNumber} />
          <FileField label="PAN card" name="panCard" accept={ACCEPTED_FILE_TYPES} required error={e.panCard}
            hint="PDF, JPEG or PNG, up to 5 MB" />
        </>
      )}
      <SubmitButton pendingLabel="Uploading…">
        {replacing ? "Replace" : "Upload"} {aadhaar ? "Aadhaar" : "PAN"}
      </SubmitButton>
    </form>
  );
}
