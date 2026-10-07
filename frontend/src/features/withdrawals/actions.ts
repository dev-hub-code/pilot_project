"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { idempotencyKeySchema } from "@/validators/order";
import { withdrawalSchema } from "@/validators/withdrawal";

/** The idempotency key is generated when the page renders, so a double submit requests once. */
export async function requestWithdrawalAction(idempotencyKey: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  const input = { bankAccountId: String(formData.get("bankAccountId") ?? ""), amount: String(formData.get("amount") ?? "") };
  const parsed = withdrawalSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch("/api/v1/withdrawals", { method: "POST", json: parsed.data, idempotencyKey });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/withdrawals");
  revalidatePath("/earnings");
  return { success: "Withdrawal requested. The amount is reserved until it is paid." };
}

export async function cancelWithdrawalAction(withdrawalId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/withdrawals/${encodeURIComponent(withdrawalId)}/cancel`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/withdrawals");
  revalidatePath("/earnings");
  return { success: "Withdrawal cancelled. The amount is back in your balance." };
}
