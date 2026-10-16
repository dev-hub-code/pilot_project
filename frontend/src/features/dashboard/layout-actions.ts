"use server";

import { revalidatePath } from "next/cache";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import type { FormState } from "@/validators/form-state";

/** Saves the employee's overview: the cards shown, in order, and the ones taken off. */
export async function saveDashboardLayoutAction(cards: string[], hidden: string[]): Promise<FormState> {
  try {
    await authFetch("/api/v1/admin/dashboard/layout", { method: "PUT", json: { cards, hidden } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin");
  return { success: "Dashboard saved." };
}

/** Back to the default overview. */
export async function resetDashboardLayoutAction(): Promise<FormState> {
  try {
    await authFetch("/api/v1/admin/dashboard/layout", { method: "DELETE" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin");
  return { success: "Dashboard reset to the default." };
}
