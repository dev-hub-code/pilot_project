"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { WithdrawalKind, WithdrawalRun } from "@/types/withdrawal";
import { type FormState, firstErrors } from "@/validators/form-state";
import { financeTaskSchema, referralModeSchema, resolveSchema, withdrawalReasonSchema, withdrawalSettingsSchema } from "@/validators/withdrawal";

const id = (value: string) => encodeURIComponent(value);

function refresh() {
  revalidatePath("/platform/withdrawals", "layout");
  revalidatePath("/platform/platform-config/withdrawals");
  revalidatePath("/platform/finance-tasks");
  revalidatePath("/platform/ledger", "layout");
}

/** Finance's update of a task: paid (with the bank reference or cash receipt), or failed (with the reason). */
/** Takes a finance task (only the member who took it may mark it), or gives it back to the queue. */
export async function financeTaskAssignmentAction(withdrawalId: string, take: boolean): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/finance-tasks/${encodeURIComponent(withdrawalId)}/${take ? "take" : "release"}`,
      { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/platform/finance-tasks");
  return { success: take ? "Assigned to you." : "Returned to the queue." };
}

export async function updateFinanceTaskAction(withdrawalId: string, outcome: "PAID" | "FAILED", _p: FormState,
  formData: FormData): Promise<FormState> {
  const parsed = financeTaskSchema.safeParse({
    outcome,
    reference: formData.get("reference") ?? "",
    reason: formData.get("reason") ?? "",
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  if (outcome === "FAILED" && !parsed.data.reason) return { fieldErrors: { reason: "Say why it could not be paid" } };
  try {
    await authFetch(`/api/v1/admin/finance-tasks/${id(withdrawalId)}`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return {
    success: outcome === "PAID"
      ? "Marked as paid: it is in the investor's total withdrawn."
      : "Marked as failed. The amount is held and a support ticket was opened for you to resolve it.",
  };
}

/**
 * Generates rent or referral withdrawals: every eligible investor's balance of that kind moves to "in progress", in a
 * bank sheet for finance to pay and a cash batch handed over at the office. The other kind is left for its own run.
 */
export async function generateWithdrawalsAction(kind: WithdrawalKind): Promise<FormState> {
  let run: WithdrawalRun;
  try {
    run = await authFetch<WithdrawalRun>(`/api/v1/admin/withdrawal-runs?kind=${kind}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  if (run.bankBatchId == null && run.cashBatchId == null) {
    return { success: `No ${kind === "RENT" ? "rent" : "referral"} to withdraw this time${run.skipped.length > 0 ? `; ${run.skipped.length} investor(s) left out` : ""}.` };
  }
  return {
    success: `${run.reference}: ${run.bankInvestors} investor(s) on the bank sheet, ${run.cashInvestors} to collect cash at the office`
      + `${run.skipped.length > 0 ? `; ${run.skipped.length} left out` : ""}.`,
  };
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
  revalidatePath(`/platform/users/${userId}`);
  return { success: "Referral payout mode saved for this year." };
}

/** Review steps with no input: take for review, give back, approve, resume from hold. */
export async function withdrawalReviewAction(withdrawalId: string, step: "review" | "review/release" | "approve" | "resume",
  _p: FormState): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/withdrawals/${id(withdrawalId)}/${step}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return {
    success: { review: "Taken for review.", "review/release": "Returned to the queue.", approve: "Approval recorded.", resume: "Back under review." }[step],
  };
}

/** Decisions that need a reason the investor sees: reject, hold, ask for more information. And an internal note. */
export async function withdrawalDecisionAction(withdrawalId: string, step: "reject" | "hold" | "request-info" | "notes",
  _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = withdrawalReasonSchema.safeParse({ reason: formData.get("reason") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/withdrawals/${id(withdrawalId)}/${step}`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, { reason: parsed.data.reason });
  }
  refresh();
  return {
    success: { reject: "Rejected. The amount is back in the investor's wallet.", hold: "Put on hold.",
      "request-info": "The investor has been asked for more information.", notes: "Note added." }[step],
  };
}

/** Hands approved requests to the payout provider; they become finance tasks, paid only with the bank's reference. */
export async function initiatePaymentAction(withdrawalIds: string[], _p: FormState): Promise<FormState> {
  let result: InitiatePaymentResult;
  try {
    result = await authFetch<InitiatePaymentResult>("/api/v1/admin/withdrawals/initiate-payment",
      { method: "POST", json: { withdrawalIds } });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  return { success: `Payment initiated: ${result.batches.map((b) => b.reference).join(", ")}. Mark it paid in Finance tasks with the bank's reference.` };
}

interface InitiatePaymentResult {
  provider: string;
  batches: { id: string; reference: string; items: number }[];
}

/** Every withdrawal setting: minimum, charges, tax, the second-approval threshold and the processing time shown. */
export async function saveWithdrawalSettingsAction(_p: FormState, formData: FormData): Promise<FormState> {
  const parsed = withdrawalSettingsSchema.safeParse(Object.fromEntries(
    ["minimumAmount", "feeFlat", "feePercent", "taxPercent", "dualApprovalThreshold", "processingDays"]
      .map((k) => [k, String(formData.get(k) ?? "")])));
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch("/api/v1/admin/settings/withdrawals", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  revalidatePath("/withdrawals");
  return { success: "Withdrawal settings saved. New requests use them from now on." };
}

/** Bulk: the approved requests ticked in the queue, handed to the payout provider together. */
export async function initiateSelectedAction(_p: FormState, formData: FormData): Promise<FormState> {
  const ids = formData.getAll("withdrawalId").map(String).filter((v) => /^[0-9a-f-]{36}$/i.test(v));
  if (ids.length === 0) return { error: "Tick the withdrawals to pay." };
  return initiatePaymentAction(ids, _p);
}
