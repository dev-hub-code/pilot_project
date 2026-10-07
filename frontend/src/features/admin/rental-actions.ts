"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { LedgerAccount, RentalReceiptDetail } from "@/types/earning";
import { adjustmentSchema, recordRentalSchema } from "@/validators/earning";
import { type FormState, firstErrors } from "@/validators/form-state";
import { idempotencyKeySchema } from "@/validators/order";
import { reasonSchema } from "@/validators/profile";

const id = (value: string) => encodeURIComponent(value);

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
}

export async function recordRentalAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = recordRentalSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let saved: RentalReceiptDetail;
  try {
    saved = await authFetch<RentalReceiptDetail>("/api/v1/admin/rentals", { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/rentals", "layout");
  redirect(`/admin/rentals/${saved.receipt.id}`);
}

export async function approveRentalAction(receiptId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/rentals/${id(receiptId)}/approve`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/rentals", "layout");
  revalidatePath("/admin/ledger", "layout");
  return { success: "Distributed. Investors have been credited." };
}

export async function rejectRentalAction(receiptId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = reasonSchema.safeParse({ reason: formData.get("reason") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(`/api/v1/admin/rentals/${id(receiptId)}/reject`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/rentals", "layout");
  return { success: "Voided. The period can be recorded again." };
}

/** One idempotency key per form render, so a double submit or retry posts the adjustment once. */
export async function adjustBalanceAction(idempotencyKey: string, _p: FormState, formData: FormData): Promise<FormState> {
  if (!idempotencyKeySchema.safeParse(idempotencyKey).success) return { error: "Reload the page and try again." };
  const input = values(formData);
  const parsed = adjustmentSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let account: LedgerAccount;
  try {
    account = await authFetch<LedgerAccount>("/api/v1/admin/ledger/adjustments", {
      method: "POST",
      json: parsed.data,
      idempotencyKey,
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/ledger", "layout");
  redirect(`/admin/ledger/${account.id}`);
}
