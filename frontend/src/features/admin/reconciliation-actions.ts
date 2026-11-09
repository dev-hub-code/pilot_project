"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { resolveUnmatchedSchema } from "@/validators/order";

/** Finance traced money that matched no payment (refunded it, or matched it by hand) and closes it with a note. */
export async function resolveUnmatchedAction(receiptId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = { reason: String(formData.get("reason") ?? "") };
  const parsed = resolveUnmatchedSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/payments/unmatched/${encodeURIComponent(receiptId)}/resolve`,
      { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/platform/payments/unmatched");
  revalidatePath("/platform/reconciliation");
  return { success: "Resolved." };
}
