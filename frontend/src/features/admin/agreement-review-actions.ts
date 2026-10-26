"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { type FormState, firstErrors } from "@/validators/form-state";

const path = (taskId: string, action: string) => `/api/v1/admin/agreement-reviews/${encodeURIComponent(taskId)}/${action}`;
const commentSchema = z.object({ body: z.string().trim().min(1, "Write a comment").max(2000, "At most 2,000 characters") });
const closingSchema = z.object({ comment: z.string().trim().max(2000, "At most 2,000 characters") });

function refresh(taskId: string) {
  revalidatePath("/admin/agreement-reviews");
  revalidatePath(`/admin/agreement-reviews/${taskId}`);
}

export async function agreementReviewAssignmentAction(taskId: string, take: boolean): Promise<FormState> {
  try {
    await authFetch(path(taskId, take ? "assign" : "unassign"), { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  refresh(taskId);
  return { success: take ? "Assigned to you." : "Returned to the queue." };
}

export async function commentOnAgreementReviewAction(taskId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = commentSchema.safeParse({ body: formData.get("body") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch(path(taskId, "comments"), { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error);
  }
  refresh(taskId);
  return { success: "Comment added." };
}

export async function completeAgreementReviewAction(taskId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const parsed = closingSchema.safeParse({ comment: formData.get("body") ?? "" });
  if (!parsed.success) return { fieldErrors: { body: parsed.error.issues[0]?.message ?? "Invalid comment" } };
  try {
    await authFetch(path(taskId, "complete"), { method: "POST", json: { comment: parsed.data.comment || null } });
  } catch (error) {
    return failureState(error);
  }
  refresh(taskId);
  return { success: "Task completed." };
}
