"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { LedgerAccount } from "@/types/earning";
import { adjustmentSchema } from "@/validators/earning";
import { type FormState, firstErrors } from "@/validators/form-state";
import { idempotencyKeySchema } from "@/validators/order";

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
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

/** Pays every monthly payout that has fallen due now, instead of waiting for the next automatic run. */
export async function runPayoutsAction(): Promise<FormState> {
  let result: { paid: number };
  try {
    result = await authFetch<{ paid: number }>("/api/v1/admin/payouts/run", { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/payouts");
  revalidatePath("/admin/ledger", "layout");
  return { success: result.paid === 0 ? "Nothing was due." : `Paid ${result.paid} payout(s) into investors' wallets.` };
}
