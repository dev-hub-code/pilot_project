"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { confirmTransferSchema, refundSchema } from "@/validators/order";

const id = (value: string) => encodeURIComponent(value);

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
}

export async function confirmTransferAction(paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = confirmTransferSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/payments/${id(paymentId)}/confirm`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/payments");
  revalidatePath("/admin/orders", "layout");
  return { success: "Transfer recorded." };
}

export async function recordRefundAction(paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = refundSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/payments/${id(paymentId)}/refund`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/payments");
  revalidatePath("/admin/orders", "layout");
  return { success: "Refund recorded." };
}
