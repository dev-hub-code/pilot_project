"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { termsSchema } from "@/validators/terms";
import { type FormState, firstErrors } from "@/validators/form-state";

/** Saves the terms and conditions as a new version, which everyone signing up from now on accepts. */
export async function saveTermsAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = { title: String(formData.get("title") ?? ""), body: String(formData.get("body") ?? "") };
  const parsed = termsSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch("/api/v1/admin/terms", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/terms");
  revalidatePath("/terms");
  return { success: "Saved. The Terms & conditions page shows this now, and everyone signing up accepts it." };
}
