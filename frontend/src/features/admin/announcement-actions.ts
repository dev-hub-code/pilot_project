"use server";

import { revalidatePath } from "next/cache";
import { authFetch } from "@/lib/server/auth/session";
import { failureState } from "@/lib/server/action-errors";
import type { Announcement } from "@/types/announcement";
import { announcementSchema } from "@/validators/announcement";
import { type FormState, firstErrors } from "@/validators/form-state";

/** Sends an announcement at once: every active account in the audience gets it as a notification. */
export async function publishAnnouncementAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = {
    title: String(formData.get("title") ?? ""),
    body: String(formData.get("body") ?? ""),
    link: String(formData.get("link") ?? ""),
    audience: String(formData.get("audience") ?? ""),
  };
  const parsed = announcementSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  let sent: Announcement;
  try {
    sent = await authFetch<Announcement>("/api/v1/admin/announcements", { method: "POST", json: parsed.data });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/announcements");
  revalidatePath("/", "layout");
  return { success: `Sent to ${sent.recipients.toLocaleString("en-IN")} account${sent.recipients === 1 ? "" : "s"}.` };
}
