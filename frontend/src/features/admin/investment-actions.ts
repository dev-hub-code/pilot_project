"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { Product } from "@/types/plan";
import { type FormState, firstErrors } from "@/validators/form-state";
import { productSchema } from "@/validators/investment";
import { reasonSchema } from "@/validators/profile";

const id = (value: string) => encodeURIComponent(value);

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
}

// ---------------------------------------------------------------------------------- plans

export async function saveProductAction(productId: string | null, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = productSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let saved: Product;
  try {
    saved = await authFetch<Product>(productId ? `/api/v1/admin/investment-products/${id(productId)}` : "/api/v1/admin/investment-products", {
      method: productId ? "PUT" : "POST",
      json: parsed.data,
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/products", "layout");
  redirect(`/admin/products/${saved.id}`);
}

export async function publishProductAction(productId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/publish`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/products", "layout");
  return { success: "Published. Investors can now invest in this plan." };
}

export async function closeProductAction(productId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/close`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/products", "layout");
  return { success: "Plan closed. Investments already made keep their term and payouts." };
}

export async function reopenProductAction(productId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/reopen`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/products", "layout");
  return { success: "Plan reopened. Investors can invest in it again." };
}

export async function cancelProductAction(productId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = reasonSchema.safeParse({ reason: formData.get("reason") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/cancel`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/products", "layout");
  return { success: "Plan cancelled." };
}
