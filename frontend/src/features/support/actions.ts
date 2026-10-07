"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { TicketDetail } from "@/types/support";
import { type FormState, firstErrors } from "@/validators/form-state";
import { openTicketSchema, replySchema } from "@/validators/support";
import { appendAttachments } from "./upload";

export async function openTicketAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = {
    subject: String(formData.get("subject") ?? ""),
    category: String(formData.get("category") ?? ""),
    related: String(formData.get("related") ?? ""),
    message: String(formData.get("message") ?? ""),
  };
  const parsed = openTicketSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  const upload = new FormData();
  upload.append("subject", parsed.data.subject);
  upload.append("category", parsed.data.category);
  upload.append("message", parsed.data.message);
  if (parsed.data.related) {
    const [type = "", id = ""] = parsed.data.related.split(":");
    upload.append("relatedType", type);
    upload.append("relatedId", id);
  }
  const fileError = appendAttachments(formData, upload);
  if (fileError) return { fieldErrors: { files: fileError }, values: input };
  let ticket: TicketDetail;
  try {
    ticket = await authFetch<TicketDetail>("/api/v1/support/tickets", { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/support");
  redirect(`/support/${ticket.ticket.id}`);
}

export async function replyTicketAction(ticketId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = { body: String(formData.get("body") ?? "") };
  const parsed = replySchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  const upload = new FormData();
  upload.append("body", parsed.data.body);
  const fileError = appendAttachments(formData, upload);
  if (fileError) return { fieldErrors: { files: fileError }, values: input };
  try {
    await authFetch(`/api/v1/support/tickets/${encodeURIComponent(ticketId)}/messages`, { method: "POST", formData: upload });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(`/support/${ticketId}`);
  return { success: "Sent." };
}

export async function closeTicketAction(ticketId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/support/tickets/${encodeURIComponent(ticketId)}/close`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/support", "layout");
  return { success: "Ticket closed." };
}
