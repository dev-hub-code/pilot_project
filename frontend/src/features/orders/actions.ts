"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { Order, Payment, PaymentMethod } from "@/types/order";
import { type FormState, firstErrors } from "@/validators/form-state";
import { cartItemSchema, depositSchema, idempotencyKeySchema } from "@/validators/order";

const id = (value: string) => encodeURIComponent(value);

// ---------------------------------------------------------------------------------- cart

export async function setCartItemAction(productId: string, then: "cart" | "stay", _p: FormState, formData: FormData): Promise<FormState> {
  const input = { quantity: String(formData.get("quantity") ?? "") };
  const parsed = cartItemSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/cart/items/${id(productId)}`, { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/cart");
  if (then === "cart") redirect("/cart");
  return { success: "Containers updated." };
}

export async function removeCartItemAction(productId: string): Promise<void> {
  await authFetch(`/api/v1/cart/items/${id(productId)}`, { method: "DELETE" });
  revalidatePath("/cart");
}

/**
 * The idempotency key is generated when the cart page renders, so a double submit or a retried
 * request places one order, never two.
 */
export async function checkoutAction(idempotencyKey: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  const acceptedTerms = formData.getAll("accept").map(String);
  const expected = Number(formData.get("lineCount") ?? 0);
  if (acceptedTerms.length === 0 || acceptedTerms.length !== expected) {
    return { error: "Read and accept the terms of every plan in your cart." };
  }
  let order: Order;
  try {
    order = await authFetch<Order>("/api/v1/orders", { method: "POST", json: { acceptedTerms }, idempotencyKey });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/cart");
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
  return { success: "Order cancelled. The reserved capacity was released." };
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
