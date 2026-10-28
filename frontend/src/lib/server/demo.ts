import "server-only";
import { cache } from "react";
import { authFetch } from "@/lib/server/auth/session";
import type { CurrentUser } from "@/types/auth";

/** Whether the signed-in user is a demo account (their pages show dummy figures); asked once per request. */
export const isDemoAccount = cache(async (): Promise<boolean> =>
  authFetch<CurrentUser>("/api/v1/auth/me").then((me) => me.demo, () => false));
