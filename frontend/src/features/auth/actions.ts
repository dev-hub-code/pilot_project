"use server";

import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import { BackendError, backendFetch } from "@/lib/server/backend-client";
import {
  type AuthTokens,
  clearSessionCookies,
  cookieNames,
  writeSessionCookies,
} from "@/lib/server/auth/cookies";
import { forwardedClientHeaders } from "@/lib/server/request-context";
import { safeRedirectPath } from "@/utils/redirect";
import { type FormState, firstErrors, loginSchema, registerSchema } from "@/validators/auth";

/** Messages for backend error codes; anything unexpected gets a generic message. */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Invalid email or password.",
  ACCOUNT_LOCKED: "Too many failed attempts. Your account is temporarily locked; try again later.",
  ACCOUNT_DISABLED: "This account is not active. Please contact support.",
  EMAIL_ALREADY_REGISTERED: "An account with this email already exists.",
  TOO_MANY_REQUESTS: "Too many attempts. Please wait a moment and try again.",
};

export async function loginAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const input = { email: formData.get("email"), password: formData.get("password") };
  const values = { email: String(input.email ?? "") };
  const parsed = loginSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };

  try {
    const tokens = await backendFetch<AuthTokens>("/api/v1/auth/login", {
      method: "POST",
      json: parsed.data,
      headers: forwardedClientHeaders(await headers()),
    });
    writeSessionCookies(await cookies(), tokens);
  } catch (error) {
    return { error: describe(error), values };
  }
  redirect(safeRedirectPath(formData.get("next")));
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
  console.error("Backend unreachable", error);
  return "The service is temporarily unavailable. Please try again.";
}
