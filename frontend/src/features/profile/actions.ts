"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { isInvestor } from "@/lib/permissions";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { getOnboarding } from "@/lib/server/onboarding";
import type { IfscLookup, KycSubmission } from "@/types/user";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { bankAccountSchema, kycNumberSchema, MAX_FILE_BYTES, profileSchema } from "@/validators/profile";

function text(formData: FormData, ...names: string[]): Record<string, string> {
  return Object.fromEntries(names.map((name) => [name, String(formData.get(name) ?? "")]));
}

export async function updateProfileAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const values = text(formData, "firstName", "lastName", "phone", "phoneCountry", "dateOfBirth", "nationality", "line1", "line2",
    "city", "stateRegion", "postalCode", "country");
  const parsed = profileSchema.safeParse(values);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };

  const p = parsed.data;
  try {
    await authFetch("/api/v1/users/me", {
      method: "PUT",
      json: {
        firstName: p.firstName,
        lastName: p.lastName,
        phone: p.phone ?? null,
        phoneCountry: p.phoneCountry,
        dateOfBirth: p.dateOfBirth ?? null,
        nationality: p.nationality,
        address: {
          line1: p.line1,
          line2: p.line2 ?? null,
          city: p.city,
          stateRegion: p.stateRegion,
          postalCode: p.postalCode,
          country: p.country,
        },
      },
    });
  } catch (error) {
    return profileFailure(failureState(error, values));
  }
  revalidatePath("/profile", "layout");
  await continueSetup("personal");
  return { success: "Profile saved." };
}

/**
 * During account setup, a finished step leads on to the next one still to do (personal details, then identity
 * verification, then a bank account). Investors who have set up their account stay where they are.
 */
async function continueSetup(finished: "personal" | "identity") {
  if (!isInvestor((await requireSession()).permissions)) return;
  const next = await getOnboarding().then((o) => o.next, () => null);
  if (next && next.key !== finished) redirect(`${next.href}?step=${finished}`);
}

/** Aadhaar or PAN: its number field and file field, as the KYC endpoint takes them. */
const KYC_DOCUMENT = {
  AADHAAR: { number: "aadhaarNumber", file: "aadhaarCard", missingFile: "Upload your Aadhaar card" },
  PAN: { number: "panNumber", file: "panCard", missingFile: "Upload your PAN card" },
} as const;

/**
 * Uploads one identity document with its number. Aadhaar and PAN may come at different times; the backend sends them
 * for review once both are in.
 */
export async function submitKycDocumentAction(document: "AADHAAR" | "PAN", _previous: FormState, formData: FormData): Promise<FormState> {
  // Identity numbers are never echoed back into the form.
  const values = {};
  const fields = KYC_DOCUMENT[document];
  const parsed = kycNumberSchema[document].safeParse(formData.get(fields.number) ?? "");
  if (!parsed.success) return { fieldErrors: { [fields.number]: parsed.error.issues[0]?.message ?? "Check the number" }, values };
  const file = formData.get(fields.file);
  if (!(file instanceof File) || file.size === 0) return { fieldErrors: { [fields.file]: fields.missingFile }, values };
  if (file.size > MAX_FILE_BYTES) return { fieldErrors: { [fields.file]: "Files must be 5 MB or smaller" }, values };

  const upload = new FormData();
  upload.append("submission", new Blob([JSON.stringify({ [fields.number]: parsed.data })], { type: "application/json" }));
  upload.append(fields.file, file, file.name);
  let result: KycSubmission;
  try {
    result = await authFetch<KycSubmission>("/api/v1/users/me/kyc", { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/profile", "layout");
  if (result.status === "PENDING") await continueSetup("identity");
  return {
    success: result.status === "PENDING"
      ? "Thank you. Both documents are in and were sent for review."
      : `${document === "AADHAAR" ? "Aadhaar" : "PAN"} saved. Upload your ${document === "AADHAAR" ? "PAN" : "Aadhaar"} too: we review them once both are in.`,
  };
}

/** The account details, the proof type and the proof file, as the bank account endpoints take them. */
function bankAccountUpload(formData: FormData): { upload: FormData } | { state: FormState } {
  const values = text(formData, "bankName", "country", "currency", "routingCode", "proofType");
  const parsed = bankAccountSchema.safeParse({ ...values, accountNumber: formData.get("accountNumber") ?? "" });
  if (!parsed.success) return { state: { fieldErrors: firstErrors(parsed.error), values } };
  const proofType = values.proofType;
  if (proofType !== "BANK_STATEMENT" && proofType !== "CANCELLED_CHEQUE") {
    return { state: { fieldErrors: { proofType: "Choose a bank statement or a cancelled cheque" }, values } };
  }
  const proof = formData.get("proof");
  if (!(proof instanceof File) || proof.size === 0) {
    return { state: { fieldErrors: { proof: "Upload your bank statement or cancelled cheque" }, values } };
  }
  if (proof.size > MAX_FILE_BYTES) return { state: { fieldErrors: { proof: "Files must be 5 MB or smaller" }, values } };
  const upload = new FormData();
  upload.append("account", new Blob([JSON.stringify(parsed.data)], { type: "application/json" }));
  upload.append("proofType", proofType);
  upload.append("proof", proof, proof.name);
  return { upload };
}

/** Where an IFSC code belongs, for the bank account form to show as it is typed. */
export async function lookupIfscAction(typed: string): Promise<IfscLookup> {
  const code = typed.trim().toUpperCase();
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(code)) return { status: "NOT_FOUND", bank: null, branch: null };
  try {
    return await authFetch<IfscLookup>(`/api/v1/users/me/bank-accounts/ifsc/${encodeURIComponent(code)}`);
  } catch {
    return { status: "UNAVAILABLE", bank: null, branch: null };
  }
}

const IFSC_NOT_FOUND = "This IFSC code is not in the RBI list of bank branches. Check it on your cheque book or bank statement";

export async function addBankAccountAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const prepared = bankAccountUpload(formData);
  if ("state" in prepared) return prepared.state;
  // Indian IFSC codes are checked online; the backend refuses an unknown one too, this puts the error on the field.
  const routingCode = String(formData.get("routingCode") ?? "");
  if (formData.get("country") === "IN" && (await lookupIfscAction(routingCode)).status === "NOT_FOUND") {
    return { fieldErrors: { routingCode: IFSC_NOT_FOUND }, values: text(formData, "bankName", "country", "routingCode", "proofType") };
  }
  try {
    await authFetch("/api/v1/users/me/bank-accounts", { method: "POST", formData: prepared.upload });
  } catch (error) {
    return failureState(error, text(formData, "bankName", "country", "routingCode", "proofType"));
  }
  revalidatePath("/profile", "layout");
  return { success: "Bank account added. It will be verified by our finance team." };
}

export async function removeBankAccountAction(accountId: string): Promise<void> {
  await authFetch(`/api/v1/users/me/bank-accounts/${encodeURIComponent(accountId)}`, { method: "DELETE" });
  revalidatePath("/profile/bank-accounts");
}

export async function makePrimaryBankAccountAction(accountId: string): Promise<void> {
  await authFetch(`/api/v1/users/me/bank-accounts/${encodeURIComponent(accountId)}/primary`, { method: "PUT" });
  revalidatePath("/profile/bank-accounts");
}

/** Puts backend errors next to their fields: "address.city" is the form's "city", a taken number is the phone's. */
function profileFailure(state: FormState): FormState {
  const fieldErrors = Object.fromEntries(Object.entries(state.fieldErrors ?? {})
    .map(([field, message]) => [field.replace(/^address\./, ""), message]));
  if (state.error?.includes("mobile number is already registered")) fieldErrors.phone = state.error;
  return Object.keys(fieldErrors).length === 0 ? state : { ...state, fieldErrors };
}
