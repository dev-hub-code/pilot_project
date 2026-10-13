"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { FormState } from "@/validators/form-state";
import { referralModeSchema } from "@/validators/withdrawal";

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
