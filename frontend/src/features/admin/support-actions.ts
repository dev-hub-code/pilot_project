"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { appendAttachments } from "@/features/support/upload";
import { type FormState, firstErrors } from "@/validators/form-state";
import { replySchema } from "@/validators/support";

const path = (ticketId: string) => `/api/v1/admin/support/tickets/${encodeURIComponent(ticketId)}`;

export async function staffReplyAction(ticketId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = { body: String(formData.get("body") ?? "") };
  const parsed = replySchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  const internal = formData.get("internal") === "on";
  const upload = new FormData();
  upload.append("body", parsed.data.body);
  upload.append("internal", String(internal));
  const fileError = appendAttachments(formData, upload);
  if (fileError) return { fieldErrors: { files: fileError }, values: input };
  try {
    await authFetch(`${path(ticketId)}/messages`, { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/support", "layout");
  return { success: internal ? "Internal note added." : "Reply sent; the customer has been notified." };
}

export async function ticketSettingAction(ticketId: string, setting: "status" | "priority" | "assign", _p: FormState,
  formData: FormData): Promise<FormState> {
  const value = String(formData.get("value") ?? "");
  const json = setting === "status" ? { status: value } : setting === "priority" ? { priority: value } : { assigneeId: value || null };
  try {
    await authFetch(`${path(ticketId)}/${setting}`, { method: "POST", json });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/support", "layout");
  return { success: "Updated." };
}
