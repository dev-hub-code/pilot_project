"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { FormState } from "@/validators/form-state";
import { idempotencyKeySchema } from "@/validators/order";
import { referralModeSchema, withdrawalRequestSchema } from "@/validators/withdrawal";

/** One idempotency key per page render, so a double submit makes one withdrawal. */
export async function requestWithdrawalAction(idempotencyKey: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  const parsed = withdrawalRequestSchema.safeParse({ balance: formData.get("balance") ?? "" });
  if (!parsed.success) return { error: "Choose a wallet to withdraw." };
  try {
    await authFetch("/api/v1/withdrawals", { method: "POST", json: parsed.data, idempotencyKey });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/withdrawals");
  return { success: "Withdrawal requested. Our team will pay it and you can follow it in History below." };
}

/** The investor's once-a-year choice of how referral earnings are paid. */
export async function chooseReferralModeAction(_p: FormState, formData: FormData): Promise<FormState> {
  const parsed = referralModeSchema.safeParse({ mode: formData.get("mode") ?? "" });
  if (!parsed.success) return { error: "Choose how to be paid." };
  try {
    await authFetch("/api/v1/withdrawals/referral-mode", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/withdrawals");
  return {
    success: parsed.data.mode === "OFFICE_CASH"
      ? "Saved. Collect your referral earnings in cash at our office each month."
      : "Saved. Referral earnings will be paid into your bank account each month.",
  };
}
