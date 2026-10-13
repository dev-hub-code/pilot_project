"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { EarningsBalance } from "@/types/earning";
import type { ReconciliationResult, ScheduledRun, Withdrawal, WithdrawalKind } from "@/types/withdrawal";
import { type FormState, firstErrors } from "@/validators/form-state";
import { reasonSchema } from "@/validators/profile";
import { markPaidSchema, referralModeSchema, resolveSchema, withdrawalDaysSchema } from "@/validators/withdrawal";

const id = (value: string) => encodeURIComponent(value);

function refresh() {
  revalidatePath("/admin/withdrawals", "layout");
  revalidatePath("/admin/ledger", "layout");
}

/** Cash withdrawn at the office: records the hand-over with the cash receipt number. */
export async function handOverCashAction(withdrawalId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const receiptNumber = String(formData.get("receiptNumber") ?? "").trim();
  if (!receiptNumber) return { fieldErrors: { receiptNumber: "Enter the cash receipt number" } };
  if (receiptNumber.length > 100) return { fieldErrors: { receiptNumber: "At most 100 characters" } };
  try {
    await authFetch(`/api/v1/admin/withdrawals/${id(withdrawalId)}/hand-over`, { method: "POST", json: { receiptNumber } });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Cash hand-over recorded. The withdrawal is paid." };
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
  return { success: "Cancelled. The amount is back in the investor's balance." };
}

export async function batchStepAction(batchId: string, step: "sent"): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/withdrawal-batches/${id(batchId)}/${step}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Marked as being paid. Upload the file with each row's status once the payments are done." };
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
  return { success: "Marked as failed. The amount is held and a support ticket was opened for you to resolve it." };
}

const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

/** The batch's Excel file uploaded back with a Status per row: completes or fails each withdrawal. */
export async function reconcileBatchAction(batchId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const file = formData.get("file");
  if (!(file instanceof File) || file.size === 0) return { fieldErrors: { file: "Choose the Excel file to upload." } };
  if (file.size > MAX_UPLOAD_BYTES) return { fieldErrors: { file: "The file must be 5 MB or smaller." } };
  if (!/\.xlsx$/i.test(file.name)) return { fieldErrors: { file: "Upload the batch's .xlsx file." } };
  const upload = new FormData();
  upload.append("file", file, file.name);
  let result: ReconciliationResult;
  try {
    result = await authFetch<ReconciliationResult>(`/api/v1/admin/withdrawal-batches/${id(batchId)}/reconciliation`, {
      method: "POST",
      formData: upload,
    });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  const { paid, failed, unchanged } = result;
  return {
    success: paid + failed === 0
      ? "Nothing to change: every row is blank or already recorded."
      : `${paid} marked paid, ${failed} marked failed${unchanged > 0 ? `, ${unchanged} unchanged` : ""}.`
        + (failed > 0 ? " A support ticket was opened for each failed payment." : ""),
  };
}

export async function updateWithdrawalDaysAction(_p: FormState, formData: FormData): Promise<FormState> {
  const parsed = withdrawalDaysSchema.safeParse({
    rentWithdrawalDay: formData.get("rentWithdrawalDay") ?? "",
    referralWithdrawalDay: formData.get("referralWithdrawalDay") ?? "",
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch("/api/v1/admin/settings/withdrawals", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: "Withdrawal days saved." };
}

/** Makes this month's rent or referral withdrawals now instead of waiting for the file day. */
export async function runWithdrawalsNowAction(kind: WithdrawalKind): Promise<FormState> {
  let run: ScheduledRun;
  try {
    run = await authFetch<ScheduledRun>(`/api/v1/admin/withdrawal-runs?kind=${kind}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  if (run.batchId) redirect(`/admin/withdrawals/batches/${run.batchId}`);
  return { success: `No balances to withdraw this month${run.skipped.length > 0 ? `; ${run.skipped.length} investor(s) skipped` : ""}.` };
}

/** Cash withdrawn at the office in front of the investor: their whole balance, then handed over against a receipt. */
export async function officeWithdrawalAction(userId: string, balance: EarningsBalance): Promise<FormState> {
  let withdrawal: Withdrawal;
  try {
    withdrawal = await authFetch<Withdrawal>("/api/v1/admin/withdrawals/office", { method: "POST", json: { userId, balance } });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  revalidatePath(`/admin/users/${userId}`);
  redirect(`/admin/withdrawals/${withdrawal.id}`);
}

/** A failed bank payment, settled: paid by hand (with the bank reference), or returned to the investor's balance. */
export async function resolveFailedAction(withdrawalId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = resolveSchema.safeParse({ outcome: formData.get("outcome") ?? "", note: formData.get("note") ?? "" });
  if (!parsed.success) return { error: "Choose how it was resolved." };
  if (parsed.data.outcome === "PAID" && !parsed.data.note) return { fieldErrors: { note: "Enter the bank reference of the payment" } };
  try {
    await authFetch(`/api/v1/admin/withdrawals/${id(withdrawalId)}/resolve`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return {
    success: parsed.data.outcome === "PAID"
      ? "Marked as paid. The ticket is resolved and the investor told."
      : "Returned to the investor's balance; it will be paid with next month's withdrawal. The ticket is resolved.",
  };
}

/** Changes an investor's referral payout mode for this year, at their request. */
export async function setReferralModeAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = referralModeSchema.safeParse({ mode: formData.get("mode") ?? "" });
  if (!parsed.success) return { error: "Choose how to pay referral earnings." };
  try {
    await authFetch(`/api/v1/admin/users/${id(userId)}/referral-mode`, { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/admin/users/${userId}`);
  return { success: "Referral payout mode saved for this year." };
}
