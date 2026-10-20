"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { FormState } from "@/validators/form-state";

/** The smallest total an order may have (0 for none); applies to orders placed from now on. */
export async function setMinimumOrderAction(_p: FormState, formData: FormData): Promise<FormState> {
  const amount = String(formData.get("amount") ?? "").replaceAll(",", "").trim();
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(amount)) {
    return { fieldErrors: { amount: "Enter an amount in rupees with up to 2 decimals (0 for no minimum)" }, values: { amount } };
  }
  try {
    await authFetch("/api/v1/admin/settings/minimum-order", { method: "PUT", json: { amount } });
  } catch (error) {
    return failureState(error, { amount });
  }
  revalidatePath("/", "layout");
  return { success: Number(amount) > 0 ? "Saved. Orders below this total can no longer be placed." : "Saved. There is no minimum order." };
}
