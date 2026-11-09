"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { paymentGatewayKeysSchema, paymentGatewaySettingsSchema } from "@/validators/payment-gateway";

/** How online payments run; applies to payments started from now on. */
export async function savePaymentGatewaySettingsAction(_p: FormState, formData: FormData): Promise<FormState> {
  const values = {
    maxAmount: String(formData.get("maxAmount") ?? ""),
    checkoutWindowMinutes: String(formData.get("checkoutWindowMinutes") ?? ""),
  };
  const parsed = paymentGatewaySettingsSchema.safeParse({
    ...values,
    onlinePayments: formData.get("onlinePayments") === "on",
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await authFetch("/api/v1/admin/settings/payment-gateway", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/", "layout");
  return { success: "Payment gateway settings saved. Payments started from now on use them." };
}

/** The Razorpay keys, webhook secret and merchant name; apply at once. Secrets left blank keep the saved ones. */
export async function savePaymentGatewayKeysAction(_p: FormState, formData: FormData): Promise<FormState> {
  // The secrets are never echoed back into the form.
  const values = {
    keyId: String(formData.get("keyId") ?? ""),
    merchantName: String(formData.get("merchantName") ?? ""),
  };
  const parsed = paymentGatewayKeysSchema.safeParse({
    ...values,
    keySecret: String(formData.get("keySecret") ?? ""),
    webhookSecret: String(formData.get("webhookSecret") ?? ""),
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await authFetch("/api/v1/admin/settings/payment-gateway/keys", { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/", "layout");
  return { success: "Razorpay keys saved. They apply to every payment from now on." };
}
