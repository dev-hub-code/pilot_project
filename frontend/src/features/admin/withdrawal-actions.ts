"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { WithdrawalKind, WithdrawalRun } from "@/types/withdrawal";
import { type FormState, firstErrors } from "@/validators/form-state";
import { financeTaskSchema, referralModeSchema, resolveSchema, withdrawalMinimumSchema } from "@/validators/withdrawal";

const id = (value: string) => encodeURIComponent(value);

function refresh() {
  revalidatePath("/admin/withdrawals", "layout");
  revalidatePath("/admin/withdrawal-runs");
  revalidatePath("/admin/finance-tasks");
  revalidatePath("/admin/ledger", "layout");
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
  revalidatePath("/admin/finance-tasks");
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
  revalidatePath(`/admin/users/${userId}`);
  return { success: "Referral payout mode saved for this year." };
}

/** The minimum an investor's row must come to for a withdrawal run to pay it; applies from the next run. */
export async function setWithdrawalMinimumAction(_p: FormState, formData: FormData): Promise<FormState> {
  const parsed = withdrawalMinimumSchema.safeParse({ minimumAmount: formData.get("minimumAmount") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch("/api/v1/admin/settings/withdrawals", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh();
  revalidatePath("/withdrawals");
  return { success: "Minimum withdrawal saved. It applies from the next run." };
}
