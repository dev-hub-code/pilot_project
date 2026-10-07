"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { StaffCredentials } from "@/types/staff";
import { type FormState, firstErrors } from "@/validators/form-state";
import { staffSchema } from "@/validators/staff";

/** Form state that can carry freshly issued credentials back to the page (never stored). */
export type CredentialsState = FormState & { credentials?: StaffCredentials };

export async function createStaffAction(_p: CredentialsState, formData: FormData): Promise<CredentialsState> {
  const input = {
    email: String(formData.get("email") ?? ""),
    firstName: String(formData.get("firstName") ?? ""),
    lastName: String(formData.get("lastName") ?? ""),
  };
  const roles = formData.getAll("roles").map(String);
  const parsed = staffSchema.safeParse({ ...input, roles });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    const credentials = await authFetch<StaffCredentials>("/api/v1/admin/staff", { method: "POST", json: parsed.data });
    revalidatePath("/admin/users", "layout");
    return { success: "Staff account created.", credentials };
  } catch (error) {
    return failureState(error, input);
  }
}

export async function resetStaffPasswordAction(userId: string): Promise<CredentialsState> {
  try {
    const credentials = await authFetch<StaffCredentials>(
      `/api/v1/admin/users/${encodeURIComponent(userId)}/temporary-password`, { method: "POST" });
    revalidatePath(`/admin/users/${userId}`);
    return { success: "New temporary password issued. They have been signed out everywhere.", credentials };
  } catch (error) {
    return failureState(error);
  }
}

export async function updateRolesAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const roles = formData.getAll("roles").map(String);
  try {
    await authFetch(`/api/v1/admin/users/${encodeURIComponent(userId)}/roles`, { method: "PUT", json: { roles } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/admin/users/${userId}`);
  return { success: "Roles updated. The user's sessions have been refreshed." };
}
