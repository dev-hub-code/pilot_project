"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import { demoAccountSchema, demoFrameSchema, demoMemberSchema, demoOrderSchema } from "@/validators/demo";
import { type FormState, firstErrors } from "@/validators/form-state";

const HERE = "/admin/demo-accounts";
const api = (userId: string) => `/api/v1/admin/demo-accounts/${encodeURIComponent(userId)}`;

function inputOf(formData: FormData): Record<string, string> {
  const input: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) input[key] = value;
  });
  return input;
}

/** Marks an investor as a demo account and opens it. */
export async function createDemoAccountAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = inputOf(formData);
  const parsed = demoAccountSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch("/api/v1/admin/demo-accounts", { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(HERE);
  redirect(`${HERE}/${parsed.data.userId}`);
}

export async function changeDemoFrameAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = inputOf(formData);
  const parsed = demoFrameSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(api(userId), { method: "PUT", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(HERE, "layout");
  return { success: "Time frame changed." };
}

export async function addDemoOrderAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = inputOf(formData);
  const parsed = demoOrderSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`${api(userId)}/orders`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(HERE, "layout");
  return { success: "Order added." };
}

export async function addDemoMemberAction(userId: string, _p: FormState, formData: FormData): Promise<FormState> {
  const input = inputOf(formData);
  const parsed = demoMemberSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await authFetch(`${api(userId)}/members`, { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath(HERE, "layout");
  return { success: "Member added." };
}

export async function removeDemoOrderAction(userId: string, orderId: string): Promise<FormState> {
  try {
    await authFetch(`${api(userId)}/orders/${encodeURIComponent(orderId)}`, { method: "DELETE" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(HERE, "layout");
  return { success: "Order removed." };
}

export async function removeDemoMemberAction(userId: string, memberId: string): Promise<FormState> {
  try {
    await authFetch(`${api(userId)}/members/${encodeURIComponent(memberId)}`, { method: "DELETE" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(HERE, "layout");
  return { success: "Member removed, with everyone below them and their orders." };
}

/** No longer a demo account: its dummy data is deleted and its pages show real figures again. */
export async function removeDemoAccountAction(userId: string): Promise<FormState> {
  try {
    await authFetch(api(userId), { method: "DELETE" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath(HERE, "layout");
  redirect(HERE);
}
