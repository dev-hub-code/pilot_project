"use server";

import { revalidatePath } from "next/cache";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import type { StaffCredentials } from "@/types/staff";
import { firstErrors } from "@/validators/form-state";
import { investorSchema } from "@/validators/investor";
import type { CredentialsState } from "./staff-actions";

/** Makes a network investor; their temporary password comes back once, to show on the page. */
export async function createInvestorAction(_p: CredentialsState, formData: FormData): Promise<CredentialsState> {
  const input = {
    firstName: String(formData.get("firstName") ?? ""),
    lastName: String(formData.get("lastName") ?? ""),
    email: String(formData.get("email") ?? ""),
    phone: String(formData.get("phone") ?? ""),
    phoneCountry: String(formData.get("phoneCountry") ?? ""),
    referralCode: String(formData.get("referralCode") ?? ""),
  };
  const parsed = investorSchema.safeParse({ ...input, signupsJoinNetwork: formData.get("signupsJoinNetwork") === "on" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    const credentials = await authFetch<StaffCredentials>("/api/v1/admin/investors", { method: "POST", json: parsed.data });
    revalidatePath("/admin/users", "layout");
    return {
      success: credentials.emailed ? "Investor account created and sign-in details emailed." : "Investor account created.",
      credentials,
    };
  } catch (error) {
    return failureState(error, input);
  }
}
