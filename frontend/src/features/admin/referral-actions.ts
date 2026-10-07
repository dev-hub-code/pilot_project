"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { scheduleRatesSchema } from "@/validators/referral";

export async function scheduleRatesAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) input[key] = value;
  });
  const parsed = scheduleRatesSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  const { level1, level2, level3, level4, effectiveFrom, reason } = parsed.data;
  try {
    await authFetch("/api/v1/admin/referral-rates", {
      method: "POST",
      json: { percents: [level1, level2, level3, level4], effectiveFrom: effectiveFrom ?? null, reason },
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/referrals");
  return { success: effectiveFrom ? "Rates scheduled." : "New rates are in force." };
}

export async function cancelRatesAction(versionId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/referral-rates/${encodeURIComponent(versionId)}/cancel`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/referrals");
  return { success: "Scheduled rates cancelled." };
}
