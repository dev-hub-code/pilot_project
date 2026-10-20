"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { FormState } from "@/validators/form-state";

const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;
const PERCENT = /^\d{1,2}(\.\d{1,2})?$/;

/** Replaces the rent bands; they apply to orders placed from now on. The backend checks they follow each other. */
export async function setRentTiersAction(_p: FormState, formData: FormData): Promise<FormState> {
  const clean = (name: string) => formData.getAll(name).map((v) => String(v).replaceAll(",", "").trim());
  const from = clean("fromAmount");
  const to = clean("toAmount");
  const percent = clean("monthlyRentPercent");
  if (from.length === 0 || from.length !== percent.length || from.length !== to.length) return { error: "Add at least one band" };
  if (from.some((v) => !AMOUNT.test(v))) return { error: "Enter each band's starting amount in rupees" };
  if (to.some((v) => v !== "" && !AMOUNT.test(v))) return { error: "Enter each band's end amount in rupees, or leave the last one blank" };
  if (percent.some((v) => !PERCENT.test(v) || Number(v) <= 0 || Number(v) > 20)) {
    return { error: "Each rent must be above 0% and at most 20%, with up to 2 decimals" };
  }
  try {
    await authFetch("/api/v1/admin/settings/rent-tiers", {
      method: "PUT",
      json: {
        tiers: from.map((fromAmount, i) => ({ fromAmount, toAmount: to[i] || null, monthlyRentPercent: percent[i] })),
      },
    });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/", "layout");
  return { success: "Saved. Orders placed from now on earn these rates; existing orders keep theirs." };
}
