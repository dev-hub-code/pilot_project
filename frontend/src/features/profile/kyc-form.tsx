"use client";

import { useActionState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { formatAadhaarInput, formatAsTyped, upperCase } from "@/utils/input";
import type { FormState } from "@/validators/form-state";
import { ACCEPTED_FILE_TYPES, FILE_RULES } from "@/validators/profile";
import { type KycDocument, submitKycDocumentAction } from "./actions";

/** Spaces the Aadhaar number as printed on the card while it is typed: "1234 5678 9012". */
const aadhaarInput = formatAsTyped(formatAadhaarInput);

const BUTTON: Record<KycDocument, string> = { AADHAAR: "Aadhaar", PAN: "PAN", SIGNATURE: "signature" };

/**
 * One identity document, Aadhaar or PAN with its number, or the signature. They can be uploaded at different times;
 * they go for review once all three are in. Name, date of birth and nationality are taken from the profile.
 */
export function KycDocumentForm({ document, replacing }: { document: KycDocument; replacing?: boolean }) {
  const [state, action] = useActionState<FormState, FormData>(submitKycDocumentAction.bind(null, document), {});
  const e = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      {document === "AADHAAR" && (
        <>
          <TextField label="Aadhaar number" name="aadhaarNumber" inputMode="numeric" autoComplete="off"
            placeholder="1234 5678 9012" maxLength={14} onChange={aadhaarInput} error={e.aadhaarNumber} />
          <FileField label="Aadhaar card" name="aadhaarCard" accept={ACCEPTED_FILE_TYPES} required error={e.aadhaarCard}
            hint={`One image with both sides. ${FILE_RULES}`} />
        </>
      )}
      {document === "PAN" && (
        <>
          <TextField label="PAN" name="panNumber" autoComplete="off" placeholder="ABCDE1234F" maxLength={10}
            autoCapitalize="characters" spellCheck={false} onChange={upperCase} error={e.panNumber} />
          <FileField label="PAN card" name="panCard" accept={ACCEPTED_FILE_TYPES} required error={e.panCard}
            hint={FILE_RULES} />
        </>
      )}
      {document === "SIGNATURE" && (
        <FileField label="Signature" name="signature" accept={ACCEPTED_FILE_TYPES} required error={e.signature}
          hint={`Sign on plain white paper, then photograph or scan it. ${FILE_RULES}`} />
      )}
      <SubmitButton pendingLabel="Uploading…">
        {replacing ? "Replace" : "Upload"} {BUTTON[document]}
      </SubmitButton>
    </form>
  );
}
