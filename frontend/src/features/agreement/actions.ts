"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { FormState } from "@/validators/form-state";

/** The investor accepts their lease agreement, which starts the lease and the rent. */
export async function acceptAgreementAction(holdingId: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (formData.get("agree") !== "on") {
    return { fieldErrors: { agree: "Tick the box to confirm you have read and accept the agreement" } };
  }
  try {
    await authFetch(`/api/v1/agreements/${encodeURIComponent(holdingId)}/accept`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/portfolio");
  revalidatePath(`/portfolio/${holdingId}/agreement`);
  // Back to the portfolio: an accepted agreement no longer opens, it is downloaded from its container.
  redirect(`/portfolio?accepted=${encodeURIComponent(holdingId)}`);
}
