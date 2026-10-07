"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { LeadDetail } from "@/types/lead";
import { type FormState, firstErrors } from "@/validators/form-state";
import { activitySchema, leadSchema, stageSchema } from "@/validators/lead";

const id = (value: string) => encodeURIComponent(value);

function values(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) out[key] = value;
  });
  return out;
}

export async function saveLeadAction(leadId: string | null, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = leadSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let saved: LeadDetail;
  try {
    saved = await authFetch<LeadDetail>(leadId ? `/api/v1/admin/leads/${id(leadId)}` : "/api/v1/admin/leads", {
      method: leadId ? "PUT" : "POST",
      json: parsed.data,
    });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/leads", "layout");
  if (!leadId) redirect(`/admin/leads/${saved.lead.id}`);
  return { success: "Lead saved." };
}

export async function changeStageAction(leadId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = stageSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/leads/${id(leadId)}/stage`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/leads", "layout");
  return { success: "Stage updated." };
}

export async function logActivityAction(leadId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = values(formData);
  const parsed = activitySchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`/api/v1/admin/leads/${id(leadId)}/activities`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(`/admin/leads/${leadId}`);
  return { success: "Logged." };
}

export async function claimLeadAction(leadId: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/leads/${id(leadId)}/claim`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/leads", "layout");
  return { success: "The lead is yours." };
}

export async function assignLeadAction(leadId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const ownerId = String(formData.get("ownerId") ?? "");
  try {
    await authFetch(`/api/v1/admin/leads/${id(leadId)}/assign`, { method: "POST", json: { ownerId: ownerId || null } });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/admin/leads", "layout");
  return { success: ownerId ? "Lead assigned." : "Returned to the unassigned pool." };
}
