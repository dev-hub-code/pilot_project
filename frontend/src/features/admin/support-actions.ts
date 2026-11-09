"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { appendAttachments } from "@/features/support/upload";
import { type FormState, firstErrors } from "@/validators/form-state";
import { replySchema } from "@/validators/support";
import { redirect } from "next/navigation";
import type { BulkAssignResult, TicketDetail } from "@/types/support";

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
  revalidatePath("/platform/support", "layout");
  return { success: internal ? "Internal note added." : "Reply sent; the customer has been notified." };
}

/** Support hands a ticket to the finance team, or finance hands it back to support. */
export async function ticketTeamAction(ticketId: string, team: "SUPPORT" | "FINANCE"): Promise<FormState> {
  try {
    await authFetch(`${path(ticketId)}/team`, { method: "POST", json: { team } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/platform/support", "layout");
  return { success: team === "FINANCE" ? "Handed to the finance team." : "Handed back to the support team." };
}

/** A finance member takes a ticket handed to finance ({@code assigneeId} is themselves), or gives it back (null). */
export async function ticketTakeAction(ticketId: string, assigneeId: string | null): Promise<FormState> {
  try {
    await authFetch(`${path(ticketId)}/assign`, { method: "POST", json: { assigneeId } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/platform/support", "layout");
  return { success: assigneeId ? "Assigned to you." : "Returned to the finance queue." };
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
  revalidatePath("/platform/support", "layout");
  return { success: "Updated." };
}

/** Assigns the ticked tickets: {@code target} is "agent:<id>" or "strategy:<ROUND_ROBIN|LEAST_LOADED>". */
export async function bulkAssignAction(_p: FormState, formData: FormData): Promise<FormState> {
  const ticketIds = formData.getAll("ticketId").map(String).filter((id) => /^[0-9a-f-]{36}$/i.test(id));
  const target = String(formData.get("target") ?? "");
  if (ticketIds.length === 0) return { error: "Tick the tickets to assign first." };
  const [kind, value] = target.split(":");
  if (!value || (kind !== "agent" && kind !== "strategy")) return { error: "Choose an agent or a way to spread them." };
  let results: BulkAssignResult[];
  try {
    results = await authFetch<BulkAssignResult[]>("/api/v1/admin/support/tickets/bulk-assign", {
      method: "POST", json: kind === "agent" ? { ticketIds, assigneeId: value } : { ticketIds, strategy: value },
    });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/platform/support", "layout");
  const done = results.filter((r) => r.assigned).length;
  const failed = results.filter((r) => !r.assigned);
  return failed.length === 0
    ? { success: `Assigned ${done} ticket${done === 1 ? "" : "s"}.` }
    : { error: `Assigned ${done}; ${failed.length} could not be: ${failed.map((r) => r.error).filter(Boolean).slice(0, 3).join("; ")}` };
}

export async function ticketCategoryAction(ticketId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const [category, subcategoryId] = String(formData.get("value") ?? "").split(":");
  try {
    await authFetch(`${path(ticketId)}/category`, { method: "POST", json: { category, subcategoryId: subcategoryId || null } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/platform/support", "layout");
  return { success: "Category updated; SLA targets follow it." };
}

export async function escalateAction(ticketId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const reason = String(formData.get("reason") ?? "").trim();
  const level = String(formData.get("level") ?? "");
  const ownerId = String(formData.get("ownerId") ?? "");
  if (!reason) return { fieldErrors: { reason: "Say why it needs escalating" }, values: { reason, level, ownerId } };
  try {
    await authFetch(`${path(ticketId)}/escalate`, {
      method: "POST", json: { reason, level: level ? Number(level) : null, ownerId: ownerId || null },
    });
  } catch (error) {
    return failureState(error, { reason, level, ownerId });
  }
  revalidatePath("/platform/support", "layout");
  return { success: "Escalated; the level's contacts have been notified." };
}

/** Opens a ticket for a customer (phone call, email) and goes to it. */
export async function staffOpenTicketAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = {
    customerId: String(formData.get("customerId") ?? ""),
    subject: String(formData.get("subject") ?? "").trim(),
    category: String(formData.get("category") ?? ""),
    subcategoryId: String(formData.get("subcategoryId") ?? ""),
    priority: String(formData.get("priority") ?? ""),
    assigneeId: String(formData.get("assigneeId") ?? ""),
    message: String(formData.get("message") ?? "").trim(),
  };
  const errors: Record<string, string> = {};
  if (!/^[0-9a-f-]{36}$/i.test(input.customerId)) errors.customerId = "Choose the customer";
  if (input.subject.length < 3) errors.subject = "Give it a short subject";
  if (!input.message) errors.message = "Write the message the customer will see";
  if (Object.keys(errors).length > 0) return { fieldErrors: errors, values: input };
  let detail: TicketDetail;
  try {
    detail = await authFetch<TicketDetail>("/api/v1/admin/support/tickets", {
      method: "POST",
      json: {
        customerId: input.customerId, subject: input.subject, category: input.category,
        subcategoryId: input.subcategoryId || null, priority: input.priority || null,
        assigneeId: input.assigneeId || null, message: input.message,
      },
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/platform/support", "layout");
  redirect(`/platform/support/${detail.ticket.id}`);
}
