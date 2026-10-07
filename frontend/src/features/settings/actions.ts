"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import { serverEnv } from "@/lib/server/env";
import { parseTheme, THEME_COOKIE } from "@/lib/theme";
import type { FormState } from "@/validators/form-state";

/** Remembers the theme on this browser for a year; the root layout applies it on every page. */
export async function setThemeAction(theme: string): Promise<void> {
  const jar = await cookies();
  const chosen = parseTheme(theme);
  if (chosen === "system") {
    jar.delete(THEME_COOKIE);
  } else {
    jar.set(THEME_COOKIE, chosen, {
      httpOnly: true, secure: serverEnv().COOKIE_SECURE, sameSite: "lax", path: "/", maxAge: 60 * 60 * 24 * 365,
    });
  }
}

export async function updateNotificationsAction(_previous: FormState, formData: FormData): Promise<FormState> {
  try {
    await authFetch("/api/v1/users/me/notification-preferences", {
      method: "PUT",
      json: {
        emailNotifications: formData.get("emailNotifications") === "on",
        smsNotifications: formData.get("smsNotifications") === "on",
      },
    });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/settings/notifications");
  return { success: "Notification preferences saved." };
}
