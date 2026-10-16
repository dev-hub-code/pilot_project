"use server";

import { revalidatePath } from "next/cache";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import type { FormState } from "@/validators/form-state";

function refresh(roleId: string) {
  revalidatePath("/admin/roles");
  revalidatePath(`/admin/roles/${roleId}`);
  revalidatePath("/admin");
}

/** The cards the role shows: the ticked ones. */
export async function chooseRoleCardsAction(roleId: string, _state: FormState, formData: FormData): Promise<FormState> {
  const cards = formData.getAll("cards").filter((v): v is string => typeof v === "string");
  try {
    await authFetch(`/api/v1/admin/roles/${encodeURIComponent(roleId)}/dashboard-cards`, { method: "PUT", json: { cards } });
  } catch (error) {
    return failureState(error);
  }
  refresh(roleId);
  return { success: `Saved. People with this role now see ${cards.length} card${cards.length === 1 ? "" : "s"} they have access to.` };
}

/** Back to every card. */
export async function resetRoleCardsAction(roleId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/roles/${encodeURIComponent(roleId)}/dashboard-cards`, { method: "DELETE" });
  } catch (error) {
    return failureState(error);
  }
  refresh(roleId);
  return { success: "This role shows every card again." };
}
