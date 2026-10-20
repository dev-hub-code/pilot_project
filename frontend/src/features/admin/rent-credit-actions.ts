"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { FormState } from "@/validators/form-state";

/** How often rent is credited; applies from the next crediting run. */
export async function setRentCreditIntervalAction(_p: FormState, formData: FormData): Promise<FormState> {
  const intervalMinutes = Number.parseInt(String(formData.get("intervalMinutes") ?? ""), 10);
  if (!Number.isInteger(intervalMinutes) || intervalMinutes <= 0) {
    return { fieldErrors: { intervalMinutes: "Choose how often rent is credited" } };
  }
  try {
    await authFetch("/api/v1/admin/settings/rent-credit", { method: "PUT", json: { intervalMinutes } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/", "layout");
  return { success: "Saved. Rent is credited at the new interval from the next run." };
}
