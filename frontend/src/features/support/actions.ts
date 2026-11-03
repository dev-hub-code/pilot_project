"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { TicketDetail } from "@/types/support";
import { type FormState, firstErrors } from "@/validators/form-state";
import { feedbackSchema, openTicketSchema, replySchema } from "@/validators/support";
import { appendAttachments } from "./upload";

export async function openTicketAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = {
    subject: String(formData.get("subject") ?? ""),
    category: String(formData.get("category") ?? ""),
    subcategoryId: String(formData.get("subcategoryId") ?? ""),
    related: String(formData.get("related") ?? ""),
    message: String(formData.get("message") ?? ""),
  };
  const parsed = openTicketSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  const upload = new FormData();
  upload.append("subject", parsed.data.subject);
  upload.append("category", parsed.data.category);
  if (parsed.data.subcategoryId) upload.append("subcategoryId", parsed.data.subcategoryId);
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

export async function reopenTicketAction(ticketId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = { body: String(formData.get("body") ?? "") };
  const parsed = replySchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/support/tickets/${encodeURIComponent(ticketId)}/reopen`, {
      method: "POST", json: { reason: parsed.data.body },
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/support", "layout");
  return { success: "Request reopened. Our support team will get back to you." };
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

/** Rates a resolved or closed request, once. */
export async function rateTicketAction(ticketId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = { rating: String(formData.get("rating") ?? ""), comment: String(formData.get("comment") ?? "") };
  const parsed = feedbackSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/support/tickets/${encodeURIComponent(ticketId)}/feedback`, {
      method: "POST", json: { rating: parsed.data.rating, comment: parsed.data.comment || null },
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(`/support/${ticketId}`);
  return { success: "Thank you for your feedback." };
}

/** Says whether a help article answered the question, once per article. */
export async function articleFeedbackAction(slug: string, helpful: boolean): Promise<FormState> {
  try {
    await authFetch(`/api/v1/support/knowledge-base/articles/${encodeURIComponent(slug)}/feedback`, {
      method: "POST", json: { helpful },
    });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(`/support/articles/${slug}`);
  return { success: "Thanks: this helps us improve our articles." };
}
