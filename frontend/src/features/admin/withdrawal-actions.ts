"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { PayoutBatchDetail } from "@/types/withdrawal";
import { type FormState, firstErrors } from "@/validators/form-state";
import { reasonSchema } from "@/validators/profile";
import { createBatchSchema, markPaidSchema } from "@/validators/withdrawal";

const id = (value: string) => encodeURIComponent(value);

function refresh() {
  revalidatePath("/admin/withdrawals", "layout");
  revalidatePath("/admin/ledger", "layout");
}

export async function approveWithdrawalAction(withdrawalId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/withdrawals/${id(withdrawalId)}/approve`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Approval recorded." };
}

export async function rejectWithdrawalAction(withdrawalId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = reasonSchema.safeParse({ reason: formData.get("reason") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/withdrawals/${id(withdrawalId)}/reject`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Rejected. The amount is back in the investor's balance." };
}

export async function createBatchAction(_p: FormState, formData: FormData): Promise<FormState> {
  const parsed = createBatchSchema.safeParse({ currency: formData.get("currency") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  let detail: PayoutBatchDetail;
  try {
    detail = await authFetch<PayoutBatchDetail>("/api/v1/admin/withdrawal-batches", { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  redirect(`/admin/withdrawals/batches/${detail.batch.id}`);
}

export async function batchStepAction(batchId: string, step: "sent" | "cancel"): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/withdrawal-batches/${id(batchId)}/${step}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: step === "sent" ? "Marked as sent. Reconcile each payment as the bank confirms it." : "Batch cancelled." };
}

export async function settleBatchAction(batchId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = markPaidSchema.safeParse({ payoutReference: formData.get("payoutReference") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/withdrawal-batches/${id(batchId)}/settle`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "All outstanding payments marked as paid." };
}

export async function markItemPaidAction(batchId: string, withdrawalId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/withdrawal-batches/${id(batchId)}/items/${id(withdrawalId)}/paid`, { method: "POST", json: {} });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Marked as paid." };
}

export async function markItemFailedAction(batchId: string, withdrawalId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = reasonSchema.safeParse({ reason: formData.get("reason") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/withdrawal-batches/${id(batchId)}/items/${id(withdrawalId)}/failed`, {
      method: "POST",
      json: parsed.data,
    });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Marked as failed. The amount is back in the investor's balance." };
}
