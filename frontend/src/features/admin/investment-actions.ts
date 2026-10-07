"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { ContainerDetail, Product } from "@/types/marketplace";
import { type FormState, firstErrors } from "@/validators/form-state";
import { containerSchema, documentSchema, productSchema, statusSchema } from "@/validators/investment";
import { reasonSchema } from "@/validators/profile";

const id = (value: string) => encodeURIComponent(value);

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
}

// ------------------------------------------------------------------------------ containers

export async function saveContainerAction(containerId: string | null, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = containerSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let saved: ContainerDetail;
  try {
    saved = await authFetch<ContainerDetail>(containerId ? `/api/v1/admin/containers/${id(containerId)}` : "/api/v1/admin/containers", {
      method: containerId ? "PUT" : "POST",
      json: { ...parsed.data, acquisitionCost: parsed.data.acquisitionCost ?? null },
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/containers", "layout");
  if (!containerId) redirect(`/admin/containers/${saved.container.id}`);
  return { success: "Container saved." };
}

export async function changeContainerStatusAction(containerId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = statusSchema.safeParse(values(formData));
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/containers/${id(containerId)}/status`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/admin/containers/${containerId}`);
  return { success: "Status updated." };
}

export async function uploadContainerDocumentAction(containerId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = documentSchema.safeParse(input);
  const file = formData.get("file");
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  if (!(file instanceof File) || file.size === 0) return { fieldErrors: { file: "Choose a file" }, values: input };

  const upload = new FormData();
  upload.append("file", file, file.name);
  const query = new URLSearchParams({
    purpose: parsed.data.purpose,
    title: parsed.data.title,
    visibleToInvestors: String(formData.get("visibleToInvestors") === "on"),
  });
  try {
    await authFetch(`/api/v1/admin/containers/${id(containerId)}/documents?${query}`, { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(`/admin/containers/${containerId}`);
  return { success: "Document uploaded." };
}

export async function setDocumentVisibilityAction(containerId: string, documentId: string, visible: boolean): Promise<void> {
  await authFetch(`/api/v1/admin/containers/${id(containerId)}/documents/${id(documentId)}`, {
    method: "PATCH",
    json: { visibleToInvestors: visible },
  });
  revalidatePath(`/admin/containers/${containerId}`);
}

// ------------------------------------------------------------------------------- offerings

export async function saveProductAction(productId: string | null, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = productSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let saved: Product;
  try {
    saved = await authFetch<Product>(productId ? `/api/v1/admin/investment-products/${id(productId)}` : "/api/v1/admin/investment-products", {
      method: productId ? "PUT" : "POST",
      json: {
        ...parsed.data,
        minimumInvestment: parsed.data.minimumInvestment ?? null,
        investmentIncrement: parsed.data.investmentIncrement ?? null,
        maximumPerInvestor: parsed.data.maximumPerInvestor ?? null,
        offerOpensAt: parsed.data.offerOpensAt ?? null,
        offerClosesAt: parsed.data.offerClosesAt ?? null,
      },
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
  return { success: "Published. The offering is now live in the marketplace." };
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
  return { success: "Offering cancelled." };
}
