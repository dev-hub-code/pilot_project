"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { reasonSchema } from "@/validators/profile";

const id = (value: string) => encodeURIComponent(value);

/** Runs a staff decision that must carry a reason (it is written to the audit log). */
async function withReason(path: string, formData: FormData, revalidate: string[], success: string,
  extra: Record<string, unknown> = {}): Promise<FormState> {
  const parsed = reasonSchema.safeParse({ reason: formData.get("reason") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(path, { method: "POST", json: { ...extra, reason: parsed.data.reason } });
  } catch (error) {
    return failureState(error);
  }
  revalidate.forEach((p) => revalidatePath(p));
  return { success };
}

export async function suspendUserAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  return withReason(`/api/v1/admin/users/${id(userId)}/suspend`, formData, [`/admin/users/${userId}`],
    "Account suspended; all sessions were signed out.");
}

export async function reactivateUserAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  return withReason(`/api/v1/admin/users/${id(userId)}/reactivate`, formData, [`/admin/users/${userId}`],
    "Account reactivated.");
}

export async function classifyInvestorAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const investorType = formData.get("investorType");
  if (investorType !== "RETAIL" && investorType !== "HNI") return { error: "Select a classification" };
  return withReason(`/api/v1/admin/users/${id(userId)}/classification`, formData, [`/admin/users/${userId}`],
    `Investor classified as ${investorType}.`, { investorType });
}

export async function approveKycAction(submissionId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/kyc/${id(submissionId)}/approve`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/kyc", "layout");
  return { success: "Verification approved." };
}

export async function rejectKycAction(submissionId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const result = await withReason(`/api/v1/admin/kyc/${id(submissionId)}/reject`, formData, [], "Verification rejected.");
  revalidatePath("/admin/kyc", "layout");
  return result;
}

export async function verifyBankAccountAction(accountId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/bank-accounts/${id(accountId)}/verify`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin", "layout");
  return { success: "Bank account verified." };
}

export async function rejectBankAccountAction(accountId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const result = await withReason(`/api/v1/admin/bank-accounts/${id(accountId)}/reject`, formData, [],
    "Bank account rejected.");
  revalidatePath("/admin", "layout");
  return result;
}
