"use server";

import { cookies, headers } from "next/headers";
import { redirect, unstable_rethrow } from "next/navigation";
import { BackendError, backendFetch } from "@/lib/server/backend-client";
import {
  type AuthTokens,
  clearSessionCookies,
  cookieNames,
  writeSessionCookies,
} from "@/lib/server/auth/cookies";
import { forwardedClientHeaders } from "@/lib/server/request-context";
import { safeRedirectPath } from "@/utils/redirect";
import { hasStaffAccess } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import { verifyAccessToken } from "@/lib/server/auth/tokens";
import { changePasswordSchema, type FormState, firstErrors, loginSchema, registerSchema } from "@/validators/auth";

/** Messages for backend error codes; anything unexpected gets a generic message. */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Invalid email or password.",
  ACCOUNT_LOCKED: "Too many failed attempts. Your account is temporarily locked; try again later.",
  ACCOUNT_DISABLED: "This account is not active. Please contact support.",
  TEMPORARY_PASSWORD_EXPIRED: "Your temporary password has expired. Ask an administrator for a new one.",
  EMAIL_ALREADY_REGISTERED: "An account with this email already exists.",
  TOO_MANY_REQUESTS: "Too many attempts. Please wait a moment and try again.",
};

export async function loginAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const input = { email: formData.get("email"), password: formData.get("password") };
  const values = { email: String(input.email ?? "") };
  const parsed = loginSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };

  let home = "/dashboard";
  try {
    const tokens = await backendFetch<AuthTokens>("/api/v1/auth/login", {
      method: "POST",
      json: parsed.data,
      headers: forwardedClientHeaders(await headers()),
    });
    writeSessionCookies(await cookies(), tokens);
    const claims = await verifyAccessToken(tokens.accessToken);
    if (claims && hasStaffAccess(claims.permissions)) home = "/admin";
  } catch (error) {
    return { error: describe(error), values };
  }
  redirect(safeRedirectPath(formData.get("next"), home));
}

export async function registerAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const input = Object.fromEntries(
    ["firstName", "lastName", "email", "password", "confirmPassword", "referralCode"].map((k) => [k, formData.get(k) ?? ""]),
  );
  const values = {
    firstName: String(input.firstName),
    lastName: String(input.lastName),
    email: String(input.email),
    referralCode: String(input.referralCode),
  };
  const parsed = registerSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };

  const { firstName, lastName, email, password, referralCode } = parsed.data;
  const body = { firstName, lastName, email, password, ...(referralCode ? { referralCode } : {}) };
  try {
    const tokens = await backendFetch<AuthTokens>("/api/v1/auth/register", {
      method: "POST",
      json: body,
      headers: forwardedClientHeaders(await headers()),
    });
    writeSessionCookies(await cookies(), tokens);
  } catch (error) {
    if (error instanceof BackendError && error.apiError?.fieldErrors?.length) {
      const fieldErrors = Object.fromEntries(error.apiError.fieldErrors.map((f) => [f.field, f.message]));
      return { fieldErrors, values };
    }
    return { error: describe(error), values };
  }
  redirect("/dashboard");
}

export async function logoutAction(): Promise<void> {
  const jar = await cookies();
  const refreshToken = jar.get(cookieNames().refresh)?.value;
  if (refreshToken) {
    try {
      await backendFetch<void>("/api/v1/auth/logout", { method: "POST", json: { refreshToken } });
    } catch (error) {
      // Cookies are cleared regardless; the session still expires server-side.
      console.error("Backend logout failed", error);
    }
  }
  clearSessionCookies(jar);
  redirect("/login");
}

function describe(error: unknown): string {
  if (error instanceof BackendError) {
    const code = error.apiError?.code;
    if (code && MESSAGES[code]) return MESSAGES[code];
    if (code === "VALIDATION_FAILED" && error.apiError?.message) return error.apiError.message;
    return `Something went wrong (reference ${error.correlationId}).`;
  }
  // Next.js navigation (e.g. a redirect for an ended session) is signalled by throwing; never swallow it.
  unstable_rethrow(error);
  console.error("Backend unreachable", error);
  return "The service is temporarily unavailable. Please try again.";
}

/**
 * Replaces the password (a temporary one, or any time from the profile). The backend ends the user's
 * other sessions; this one is renewed so its token no longer requires a password change.
 */
export async function changePasswordAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const parsed = changePasswordSchema.safeParse({
    currentPassword: formData.get("currentPassword") ?? "",
    newPassword: formData.get("newPassword") ?? "",
    confirmPassword: formData.get("confirmPassword") ?? "",
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error) };
  try {
    await authFetch("/api/v1/auth/password", {
      method: "POST",
      json: { currentPassword: parsed.data.currentPassword, newPassword: parsed.data.newPassword },
    });
  } catch (error) {
    if (error instanceof BackendError && error.apiError?.code === "INVALID_CREDENTIALS") {
      return { fieldErrors: { currentPassword: "Current password is incorrect" } };
    }
    if (error instanceof BackendError && error.apiError?.fieldErrors?.length) {
      return { fieldErrors: Object.fromEntries(error.apiError.fieldErrors.map((f) => [f.field, f.message])) };
    }
    if (error instanceof BackendError && error.status < 500 && error.apiError?.message) {
      return { error: error.apiError.message };
    }
    return { error: describe(error) };
  }
  const jar = await cookies();
  const refreshToken = jar.get(cookieNames().refresh)?.value;
  let destination = "/dashboard";
  if (refreshToken) {
    try {
      const tokens = await backendFetch<AuthTokens>("/api/v1/auth/refresh", {
        method: "POST",
        json: { refreshToken },
        headers: forwardedClientHeaders(await headers()),
      });
      writeSessionCookies(jar, tokens);
      const claims = await verifyAccessToken(tokens.accessToken);
      if (claims && hasStaffAccess(claims.permissions)) destination = "/admin";
    } catch {
      destination = "/login";
    }
  }
  redirect(destination);
}
