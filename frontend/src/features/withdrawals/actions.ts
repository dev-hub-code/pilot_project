"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { firstErrors, type FormState } from "@/validators/form-state";
import { idempotencyKeySchema } from "@/validators/order";
import { referralModeSchema, withdrawalRequestSchema, withdrawalRespondSchema } from "@/validators/withdrawal";
import type { EarningsBalance } from "@/types/earning";
import type { Withdrawal, WithdrawalQuote } from "@/types/withdrawal";

const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;

/** The backend's quote for an amount: charges, tax, what is paid and what stays in the wallet. */
export async function quoteWithdrawalAction(balance: EarningsBalance, amount: string): Promise<WithdrawalQuote | { error: string }> {
  if (!AMOUNT.test(amount.trim())) return { error: "Enter an amount in rupees, up to 2 decimals" };
  try {
    const params = new URLSearchParams({ balance, amount: amount.trim() });
    return await authFetch<WithdrawalQuote>(`/api/v1/withdrawals/quote?${params}`);
  } catch (error) {
    return { error: failureState(error).error ?? "Could not work out the amount. Try again." };
  }
}

/**
 * Submits the request, confirmed with the investor's password (never echoed back). One idempotency key per page
 * render, so a double submit makes one withdrawal.
 */
export async function requestWithdrawalAction(idempotencyKey: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  const parsed = withdrawalRequestSchema.safeParse({
    balance: formData.get("balance") ?? "",
    amount: formData.get("amount") ?? "",
    bankAccountId: formData.get("bankAccountId") ?? "",
    password: formData.get("password") ?? "",
    confirmed: formData.get("confirmed") ?? "",
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), error: "Check the details and try again." };
  const { confirmed: _confirmed, ...body } = parsed.data;
  let withdrawal: Withdrawal;
  try {
    withdrawal = await authFetch<Withdrawal>("/api/v1/withdrawals", { method: "POST", json: body, idempotencyKey });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/withdrawals");
  return { success: `Withdrawal ${withdrawal.reference} submitted.`, values: { id: withdrawal.id, reference: withdrawal.reference } };
}

export async function cancelWithdrawalAction(withdrawalId: string, _p: FormState): Promise<FormState> {
  try {
    await authFetch(`/api/v1/withdrawals/${encodeURIComponent(withdrawalId)}/cancel`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/withdrawals");
  revalidatePath(`/withdrawals/${withdrawalId}`);
  return { success: "Cancelled. The amount is back in your wallet." };
}

export async function respondWithdrawalAction(withdrawalId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = withdrawalRespondSchema.safeParse({ message: formData.get("message") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/withdrawals/${encodeURIComponent(withdrawalId)}/respond`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, { message: parsed.data.message });
  }
  revalidatePath(`/withdrawals/${withdrawalId}`);
  return { success: "Thanks. Your answer is with our finance team." };
}

/** The investor's choice of how referral earnings are paid. */
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
