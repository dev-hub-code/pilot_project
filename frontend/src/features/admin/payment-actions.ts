"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { companyBankAccountSchema, confirmTransferSchema, refundSchema, rejectPaymentSchema } from "@/validators/order";

const id = (value: string) => encodeURIComponent(value);

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
}

export async function confirmTransferAction(paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = confirmTransferSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/payments/${id(paymentId)}/confirm`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/payments");
  revalidatePath("/admin/orders", "layout");
  return { success: "Transfer recorded." };
}

export async function rejectPaymentAction(paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = rejectPaymentSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/payments/${id(paymentId)}/reject`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/payments");
  revalidatePath("/admin/orders", "layout");
  return { success: "Payment rejected. The investor can pay again while the order is open." };
}

export async function recordRefundAction(paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = refundSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/payments/${id(paymentId)}/refund`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/payments");
  revalidatePath("/admin/orders", "layout");
  return { success: "Refund recorded." };
}

// ---------------------------------------------------------------- company bank accounts

/** Creates an account, or updates {@code accountId} when given. */
export async function saveCompanyBankAccountAction(accountId: string | null, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = companyBankAccountSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(accountId ? `/api/v1/admin/company-bank-accounts/${id(accountId)}` : "/api/v1/admin/company-bank-accounts",
      { method: accountId ? "PUT" : "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/company-bank-accounts");
  return accountId ? { success: "Account updated.", values: input } : { success: "Account added. Investors can now pay into it." };
}

export async function setCompanyBankAccountActiveAction(accountId: string, active: boolean): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/company-bank-accounts/${id(accountId)}/${active ? "activate" : "deactivate"}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/company-bank-accounts");
  return { success: active ? "Account activated." : "Account deactivated." };
}
