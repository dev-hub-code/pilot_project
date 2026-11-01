"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { Order, Payment, PaymentMethod } from "@/types/order";
import { type FormState, firstErrors } from "@/validators/form-state";
import { depositSchema, idempotencyKeySchema, receiptSchema, unitsSchema } from "@/validators/order";

const id = (value: string) => encodeURIComponent(value);

// --------------------------------------------------------------------------------- buying

/**
 * Buys units of a plan. The idempotency key is generated when the plan page renders, so a double submit or a retried
 * request places one order, never two.
 */
export async function placeOrderAction(productId: string, idempotencyKey: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  const input = { units: String(formData.get("units") ?? "") };
  const units = unitsSchema.safeParse(input.units);
  if (!units.success) return { fieldErrors: { units: firstErrors(units.error)[""] ?? "Enter the number of units" }, values: input };
  if (formData.get("accept") !== "on") return { error: "Read and accept the plan's terms to invest.", values: input };
  let order: Order;
  try {
    order = await authFetch<Order>("/api/v1/orders", {
      method: "POST",
      json: { productId, units: units.data, acceptTerms: true },
      idempotencyKey,
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/orders");
  redirect(`/orders/${order.id}`);
}

// -------------------------------------------------------------------------------- orders

export async function cancelOrderAction(orderId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/orders/${id(orderId)}/cancel`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/orders/${orderId}`);
  revalidatePath("/orders");
  return { success: "Order cancelled." };
}

export async function startPaymentAction(orderId: string, method: PaymentMethod, idempotencyKey: string): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  try {
    await authFetch<Payment>(`/api/v1/orders/${id(orderId)}/payments`, { method: "POST", json: { method }, idempotencyKey });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/orders/${orderId}`);
  return {};
}

/** The investor tells us how they paid a bank payment, for finance to verify. */
export async function submitDepositAction(orderId: string, paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = {
    companyBankAccountId: String(formData.get("companyBankAccountId") ?? ""),
    mode: String(formData.get("mode") ?? ""),
    reference: String(formData.get("reference") ?? ""),
  };
  const parsed = depositSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch<Payment>(`/api/v1/payments/${id(paymentId)}/deposit`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(`/orders/${orderId}`);
  return { success: "Thank you. We will confirm your order once the payment is verified.", values: input };
}

/** The investor gives the receipt number the office handed them for their cash, for finance to confirm. */
export async function submitReceiptAction(orderId: string, paymentId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = { receiptNumber: String(formData.get("receiptNumber") ?? "") };
  const parsed = receiptSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch<Payment>(`/api/v1/payments/${id(paymentId)}/receipt`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(`/orders/${orderId}`);
  return { success: "Thank you. We will confirm your order once our finance team records the cash.", values: input };
}
