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

export async function disableUserAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  return withReason(`/api/v1/admin/users/${id(userId)}/disable`, formData, [`/admin/users/${userId}`],
    "Account disabled; all sessions were signed out and its earnings are held back.");
}

export async function enableUserAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  return withReason(`/api/v1/admin/users/${id(userId)}/enable`, formData, [`/admin/users/${userId}`],
    "Account enabled. Returns held back while it was disabled is credited at the next run.");
}

/** Takes a KYC review or bank-account verification, or gives it back: only the assignee may decide it. */
async function assignment(path: string, success: string, revalidate: string): Promise<FormState> {
  try {
    await authFetch(path, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(revalidate, "layout");
  return { success };
}

export async function assignKycAction(submissionId: string): Promise<FormState> {
  return assignment(`/api/v1/admin/kyc/${id(submissionId)}/assign`, "Assigned to you.", "/admin/kyc");
}

export async function unassignKycAction(submissionId: string): Promise<FormState> {
  return assignment(`/api/v1/admin/kyc/${id(submissionId)}/unassign`, "Returned to the queue.", "/admin/kyc");
}

export async function assignBankAccountAction(accountId: string): Promise<FormState> {
  return assignment(`/api/v1/admin/bank-accounts/${id(accountId)}/assign`, "Assigned to you.", "/admin");
}

export async function unassignBankAccountAction(accountId: string): Promise<FormState> {
  return assignment(`/api/v1/admin/bank-accounts/${id(accountId)}/unassign`, "Returned to the queue.", "/admin");
}

/** Takes or gives back a KYC review and bank accounts together, in one step; anything already done stays done. */
async function assignAll(paths: string[], success: string): Promise<FormState> {
  try {
    for (const path of paths) await authFetch(path, { method: "POST" });
  } catch (error) {
    revalidatePath("/admin", "layout");
    return failureState(error);
  }
  revalidatePath("/admin", "layout");
  return { success };
}

const verificationPaths = (kycId: string | null, bankIds: string[], step: "assign" | "unassign") => [
  ...(kycId ? [`/api/v1/admin/kyc/${id(kycId)}/${step}`] : []),
  ...bankIds.map((bankId) => `/api/v1/admin/bank-accounts/${id(bankId)}/${step}`),
];

export async function assignVerificationAction(kycId: string | null, bankIds: string[]): Promise<FormState> {
  return assignAll(verificationPaths(kycId, bankIds, "assign"), "Assigned to you.");
}

export async function unassignVerificationAction(kycId: string | null, bankIds: string[]): Promise<FormState> {
  return assignAll(verificationPaths(kycId, bankIds, "unassign"), "Returned to the queue.");
}

export async function approveKycAction(submissionId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/kyc/${id(submissionId)}/approve`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/kyc", "layout");
  revalidatePath("/admin/verification");
  return { success: "Verification approved." };
}

export async function rejectKycAction(submissionId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const result = await withReason(`/api/v1/admin/kyc/${id(submissionId)}/reject`, formData, [], "Verification rejected.");
  revalidatePath("/admin/kyc", "layout");
  revalidatePath("/admin/verification");
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

