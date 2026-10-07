"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authFetch } from "@/lib/server/auth/session";

/** Marks one notification read and opens what it points at (only paths inside this app). */
export async function openNotificationAction(notificationId: string, link: string | null): Promise<void> {
  await authFetch(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, { method: "POST" });
  revalidatePath("/", "layout");
  redirect(link && link.startsWith("/") && !link.startsWith("//") ? link : "/notifications");
}

export async function readAllNotificationsAction(): Promise<void> {
  await authFetch("/api/v1/notifications/read-all", { method: "POST" });
  revalidatePath("/", "layout");
}
