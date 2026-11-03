"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { isInvestor } from "@/lib/permissions";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { type OnboardingStep, getOnboarding } from "@/lib/server/onboarding";
import { missingPersonalDetails } from "@/features/profile/kyc-labels";
import type { IfscLookup, KycSubmission, Profile } from "@/types/user";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { bankAccountSchema, fileProblem, kycNumberSchema, profileSchema } from "@/validators/profile";

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
        // Once saved, nationality is not on the form: send nothing so the saved one is kept, not the default.
        nationality: formData.has("nationality") ? p.nationality : null,
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
  const next = await continueSetup("personal");
  // Saved, but setup cannot go on without what is still blank: say so at the fields, not just "saved".
  if (next?.key === "personal") return stillMissing(await authFetch<Profile>("/api/v1/users/me"), values);
  return { success: "Profile saved." };
}

/** Each personal detail setup needs, by the form field that holds it. */
const NEEDED: Record<string, { field: string; message: string }> = {
  "mobile phone": { field: "phone", message: "Enter your mobile number" },
  "date of birth": { field: "dateOfBirth", message: "Enter your date of birth (DD/MM/YYYY)" },
  nationality: { field: "nationality", message: "Select your nationality" },
  address: { field: "line1", message: "Complete your address" },
};

function stillMissing(profile: Profile, values: Record<string, string>): FormState {
  const missing = missingPersonalDetails(profile);
  return {
    error: `Saved. To continue setting up your account, add your ${missing.join(", ")}.`,
    fieldErrors: Object.fromEntries(missing.flatMap((m) => (NEEDED[m] ? [[NEEDED[m].field, NEEDED[m].message]] : []))),
    values,
  };
}

/**
 * During account setup, a finished step leads on to the next one still to do (personal details, then identity
 * verification, then a bank account). Investors who have set up their account stay where they are.
 *
 * @return the step still to do (the finished one, when it is not complete after all); null when setup is done or the
 *         user is not an investor
 */
async function continueSetup(finished: "personal" | "identity"): Promise<OnboardingStep | null> {
  if (!isInvestor((await requireSession()).permissions)) return null;
  const next = await getOnboarding().then((o) => o.next, () => null);
  if (next && next.key !== finished) redirect(`${next.href}?step=${finished}`);
  return next;
}

/** Aadhaar, PAN or signature: its number field (the signature has none) and file field, as the KYC endpoint takes them. */
const KYC_DOCUMENT = {
  AADHAAR: { label: "Aadhaar", number: "aadhaarNumber", file: "aadhaarCard", missingFile: "Upload your Aadhaar card" },
  PAN: { label: "PAN", number: "panNumber", file: "panCard", missingFile: "Upload your PAN card" },
  SIGNATURE: { label: "Signature", number: null, file: "signature", missingFile: "Upload a photo of your signature" },
} as const;

export type KycDocument = keyof typeof KYC_DOCUMENT;

/**
 * Uploads one identity document (with its number) or the signature. They may come at different times; the backend
 * sends them for review once all three are in.
 */
export async function submitKycDocumentAction(document: KycDocument, _previous: FormState, formData: FormData): Promise<FormState> {
  // Identity numbers are never echoed back into the form.
  const values = {};
  const fields = KYC_DOCUMENT[document];
  let number: string | null = null;
  if (fields.number) {
    const parsed = kycNumberSchema[fields.number === "aadhaarNumber" ? "AADHAAR" : "PAN"].safeParse(formData.get(fields.number) ?? "");
    if (!parsed.success) return { fieldErrors: { [fields.number]: parsed.error.issues[0]?.message ?? "Check the number" }, values };
    number = parsed.data;
  }
  const file = formData.get(fields.file);
  if (!(file instanceof File) || file.size === 0) return { fieldErrors: { [fields.file]: fields.missingFile }, values };
  const problem = fileProblem(file);
  if (problem) return { fieldErrors: { [fields.file]: problem }, values };

  const upload = new FormData();
  upload.append("submission", new Blob([JSON.stringify(fields.number ? { [fields.number]: number } : {})], { type: "application/json" }));
  upload.append(fields.file, file, file.name);
  let result: KycSubmission;
  try {
    result = await authFetch<KycSubmission>("/api/v1/users/me/kyc", { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/profile", "layout");
  if (result.status === "PENDING") await continueSetup("identity");
  if (result.status === "PENDING") return { success: "Thank you. Your Aadhaar, PAN and signature are in and were sent for review." };
  const has = new Set(result.documents.map((d) => d.purpose));
  const still = [
    !has.has("KYC_AADHAAR_CARD") && "Aadhaar",
    !has.has("KYC_PAN_CARD") && "PAN",
    !has.has("KYC_SIGNATURE") && "signature",
  ].filter(Boolean).join(" and ");
  return { success: `${fields.label} saved. Upload your ${still} too: we review them once all are in.` };
}

/** The account details, the proof type and the proof file, as the bank account endpoints take them. */
function bankAccountUpload(formData: FormData): { upload: FormData } | { state: FormState } {
  const values = text(formData, "country", "currency", "routingCode", "proofType");
  const parsed = bankAccountSchema.safeParse({ ...values, accountNumber: formData.get("accountNumber") ?? "" });
  if (!parsed.success) return { state: { fieldErrors: firstErrors(parsed.error), values } };
  const proofType = values.proofType;
  if (proofType !== "BANK_STATEMENT" && proofType !== "CANCELLED_CHEQUE" && proofType !== "PASSBOOK") {
    return { state: { fieldErrors: { proofType: "Choose a bank statement, a cancelled cheque or a passbook" }, values } };
  }
  const proof = formData.get("proof");
  if (!(proof instanceof File) || proof.size === 0) {
    return { state: { fieldErrors: { proof: "Upload your bank statement, cancelled cheque or passbook" }, values } };
  }
  const proofProblem = fileProblem(proof);
  if (proofProblem) return { state: { fieldErrors: { proof: proofProblem }, values } };
  const upload = new FormData();
  upload.append("account", new Blob([JSON.stringify(parsed.data)], { type: "application/json" }));
  upload.append("proofType", proofType);
  upload.append("proof", proof, proof.name);
  return { upload };
}

/** The bank an IFSC code belongs to, for the bank account form to fill in as the code is typed. */
export async function lookupIfscAction(typed: string): Promise<IfscLookup> {
  const code = typed.trim().toUpperCase();
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(code)) return { status: "NOT_FOUND", bank: null };
  try {
    return await authFetch<IfscLookup>(`/api/v1/users/me/bank-accounts/ifsc/${encodeURIComponent(code)}`);
  } catch {
    return { status: "UNAVAILABLE", bank: null };
  }
}

export async function addBankAccountAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const prepared = bankAccountUpload(formData);
  if ("state" in prepared) return prepared.state;
  try {
    await authFetch("/api/v1/users/me/bank-accounts", { method: "POST", formData: prepared.upload });
  } catch (error) {
    return failureState(error, text(formData, "country", "routingCode", "proofType"));
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
