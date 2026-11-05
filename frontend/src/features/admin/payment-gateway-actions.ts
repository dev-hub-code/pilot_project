"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";
import { paymentGatewaySettingsSchema } from "@/validators/payment-gateway";

/** How online payments run; applies to payments started from now on. */
export async function savePaymentGatewaySettingsAction(_p: FormState, formData: FormData): Promise<FormState> {
  const values = {
    maxAmount: String(formData.get("maxAmount") ?? ""),
    checkoutWindowMinutes: String(formData.get("checkoutWindowMinutes") ?? ""),
    virtualAccountWindowHours: String(formData.get("virtualAccountWindowHours") ?? ""),
  };
  const parsed = paymentGatewaySettingsSchema.safeParse({
    ...values,
    onlinePayments: formData.get("onlinePayments") === "on",
    virtualAccounts: formData.get("virtualAccounts") === "on",
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
