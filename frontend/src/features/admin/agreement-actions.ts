"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { AgreementPreview, LeaseAgreement } from "@/types/agreement";
import { agreementTemplateSchema } from "@/validators/agreement";
import { type FormState, firstErrors } from "@/validators/form-state";

export interface AgreementFormState extends FormState {
  preview?: AgreementPreview;
}

/** Previews the wording with example values, or saves it as a new version ("intent" says which). */
export async function agreementTemplateAction(_p: AgreementFormState, formData: FormData): Promise<AgreementFormState> {
  const input = { title: String(formData.get("title") ?? ""), body: String(formData.get("body") ?? "") };
  const parsed = agreementTemplateSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  const preview = formData.get("intent") === "preview";
  try {
    if (preview) {
      const filled = await authFetch<AgreementPreview>("/api/v1/admin/agreement-template/preview", {
        method: "POST",
        json: parsed.data,
      });
      return { preview: filled, values: input };
    }
    await authFetch("/api/v1/admin/agreement-template", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/agreement");
  return { success: "Saved. Agreements issued from now on use this wording." };
}

/** Issues agreements for an order's containers assigned before agreements were issued. */
export async function issueMissingAgreementsAction(orderId: string): Promise<FormState> {
  try {
    const issued = await authFetch<LeaseAgreement[]>(
      `/api/v1/admin/container-assignments/orders/${encodeURIComponent(orderId)}/agreements`, { method: "POST" });
    revalidatePath(`/admin/orders/${orderId}`);
    return { success: `${issued.length} agreement${issued.length === 1 ? "" : "s"} on this order.` };
  } catch (error) {
    return failureState(error);
  }
}
