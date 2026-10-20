"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { PlanPhoto, Product } from "@/types/marketplace";
import { MAX_PLAN_PHOTOS, chosenPhotos, photoProblem } from "./plan-photos";
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
  // A new plan can be created with its photos; they are uploaded once the plan exists.
  const photos = productId ? [] : chosenPhotos(formData);
  const photoError = photoProblem(photos, MAX_PLAN_PHOTOS);
  if (photoError) return { fieldErrors: { photos: `${photoError} (choose them again)` }, values: input };
  let saved: Product;
  try {
    saved = await authFetch<Product>(productId ? `/api/v1/admin/investment-products/${id(productId)}` : "/api/v1/admin/investment-products", {
      method: productId ? "PUT" : "POST",
      json: parsed.data,
    });
  } catch (error) {
    return failureState(error, input);
  }
  const failed = await uploadPhotos(saved.id, photos);
  revalidatePath("/admin/products", "layout");
  redirect(`/admin/products/${saved.id}${failed > 0 ? `?photosFailed=${failed}` : ""}`);
}

/** Uploads the photos one by one, in the order chosen; returns how many could not be uploaded. */
async function uploadPhotos(productId: string, files: File[]): Promise<number> {
  let failed = 0;
  for (const file of files) {
    const upload = new FormData();
    upload.append("file", file, file.name);
    try {
      await authFetch(`/api/v1/admin/investment-products/${id(productId)}/photos`, { method: "POST", formData: upload });
    } catch (error) {
      console.error("Plan photo upload failed", file.name, error);
      failed++;
    }
  }
  return failed;
}

export async function publishProductAction(productId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/publish`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/products", "layout");
  return { success: "Published. Investors can now buy these containers." };
}

export async function closeProductAction(productId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/close`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/products", "layout");
  return { success: "Listing closed. Containers already sold keep their lease and payouts." };
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
  return { success: "Listing cancelled." };
}

// ---------------------------------------------------------------------------- plan photos

/** Adds JPEG or PNG photos to the plan (several at once), shown in the marketplace in the order uploaded. */
export async function uploadPlanPhotosAction(productId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const files = chosenPhotos(formData);
  if (files.length === 0) return { fieldErrors: { photos: "Choose one or more photos" } };
  let current: PlanPhoto[];
  try {
    current = await authFetch<PlanPhoto[]>(`/api/v1/admin/investment-products/${id(productId)}/photos`);
  } catch (error) {
    return failureState(error);
  }
  const problem = photoProblem(files, MAX_PLAN_PHOTOS - current.length);
  if (problem) return { fieldErrors: { photos: problem } };
  const failed = await uploadPhotos(productId, files);
  revalidatePath(`/admin/products/${productId}`);
  const added = files.length - failed;
  if (failed > 0) {
    return { error: `${added} of ${files.length} photos added; ${failed} could not be uploaded. Try those again.` };
  }
  return { success: added === 1 ? "Photo added." : `${added} photos added.` };
}

export async function removePlanPhotoAction(productId: string, photoId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/investment-products/${id(productId)}/photos/${id(photoId)}`, { method: "DELETE" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/admin/products/${productId}`);
  return { success: "Photo removed." };
}
