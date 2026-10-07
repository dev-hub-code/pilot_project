"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { bankAccountSchema, kycSchema, MAX_FILE_BYTES, profileSchema, taxSchema } from "@/validators/profile";

function text(formData: FormData, ...names: string[]): Record<string, string> {
  return Object.fromEntries(names.map((name) => [name, String(formData.get(name) ?? "")]));
}

export async function updateProfileAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const values = text(formData, "firstName", "lastName", "phone", "dateOfBirth", "nationality", "line1", "line2",
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
        dateOfBirth: p.dateOfBirth ?? null,
        nationality: p.nationality ?? null,
        address: {
          line1: p.line1 ?? null,
          line2: p.line2 ?? null,
          city: p.city ?? null,
          stateRegion: p.stateRegion ?? null,
          postalCode: p.postalCode ?? null,
          country: p.country ?? null,
        },
      },
    });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/profile");
  return { success: "Profile saved." };
}

export async function updateTaxAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const values = text(formData, "taxResidencyCountry");
  const parsed = taxSchema.safeParse({ ...values, taxId: formData.get("taxId") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await authFetch("/api/v1/users/me/tax", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/profile");
  return { success: "Tax information saved." };
}

const KYC_FILES = ["identityFront", "identityBack", "selfie", "proofOfAddress"] as const;

export async function submitKycAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const values = text(formData, "legalFirstName", "legalLastName", "dateOfBirth", "nationality", "documentType",
    "documentIssuingCountry", "documentExpiryDate");
  const parsed = kycSchema.safeParse({ ...values, documentNumber: formData.get("documentNumber") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };

  const upload = new FormData();
  upload.append("submission", new Blob([JSON.stringify(parsed.data)], { type: "application/json" }));
  for (const name of KYC_FILES) {
    const file = formData.get(name);
    if (file instanceof File && file.size > 0) {
      if (file.size > MAX_FILE_BYTES) {
        return { fieldErrors: { [name]: "Files must be 5 MB or smaller" }, values };
      }
      upload.append(name, file, file.name);
    }
  }
  try {
    await authFetch("/api/v1/users/me/kyc", { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/profile", "layout");
  return { success: "Thank you. Your documents were submitted for review." };
}

export async function addBankAccountAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const values = text(formData, "accountHolderName", "bankName", "country", "currency", "routingCode");
  const parsed = bankAccountSchema.safeParse({ ...values, accountNumber: formData.get("accountNumber") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await authFetch("/api/v1/users/me/bank-accounts", { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/profile/bank-accounts");
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
