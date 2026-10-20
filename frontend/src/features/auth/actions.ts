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
import {
  changePasswordSchema,
  type FormState,
  firstErrors,
  loginSchema,
  registerSchema,
  forgotPasswordSchema,
  resendVerificationSchema,
  resetPasswordSchema,
  verifyCodeSchema,
} from "@/validators/auth";

/** Messages for backend error codes; anything unexpected gets a generic message. */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Invalid email or password.",
  ACCOUNT_LOCKED: "Too many failed attempts. Your account is temporarily locked; try again later.",
  ACCOUNT_DISABLED: "This account is not active. Please contact support.",
  TEMPORARY_PASSWORD_EXPIRED: "Your temporary password has expired. Ask an administrator for a new one.",
  EMAIL_ALREADY_REGISTERED: "An account with this email already exists.",
  EMAIL_NOT_VERIFIED: "Please confirm your email address first, with the code or link we emailed when you signed up.",
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
    const code = error instanceof BackendError ? error.apiError?.code : undefined;
    return { error: describe(error), values, code };
  }
  redirect(safeRedirectPath(formData.get("next"), home));
}

export async function registerAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const input = Object.fromEntries(
    ["firstName", "lastName", "email", "phone", "phoneCountry", "password", "confirmPassword", "referralCode"].map((k) => [k, formData.get(k) ?? ""]),
  );
  const values = {
    firstName: String(input.firstName),
    lastName: String(input.lastName),
    email: String(input.email),
    phone: String(input.phone),
    phoneCountry: String(input.phoneCountry),
    referralCode: String(input.referralCode),
  };
  const parsed = registerSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };

  const { firstName, lastName, email, phone, phoneCountry, password, referralCode } = parsed.data;
  const body = { firstName, lastName, email, phone, phoneCountry, password, ...(referralCode ? { referralCode } : {}) };
  let verifyEmail: string | null = null;
  try {
    const result = await backendFetch<AuthTokens | RegistrationPending>("/api/v1/auth/register", {
      method: "POST",
      json: body,
      headers: forwardedClientHeaders(await headers()),
    });
    if ("emailVerificationRequired" in result) verifyEmail = result.email;
    else writeSessionCookies(await cookies(), result);
  } catch (error) {
    if (error instanceof BackendError && error.apiError?.fieldErrors?.length) {
      const fieldErrors = Object.fromEntries(error.apiError.fieldErrors.map((f) => [f.field, f.message]));
      return { fieldErrors, values };
    }
    // A number already on another account is shown next to the field.
    if (error instanceof BackendError && error.apiError?.message?.includes("mobile number is already registered")) {
      return { fieldErrors: { phone: error.apiError.message }, values };
    }
    return { error: describe(error), values };
  }
  // No session yet: the account is activated by the link emailed to the user.
  if (verifyEmail) redirect(`/verify-email?email=${encodeURIComponent(verifyEmail)}`);
  redirect("/dashboard");
}

/** The backend's answer to a sign-up that must confirm its email address before signing in. */
interface RegistrationPending {
  emailVerificationRequired: true;
  email: string;
}

/** Sends a fresh verification link. The answer is the same whether or not the address is registered. */
export async function resendVerificationAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  const values = { email };
  const parsed = resendVerificationSchema.safeParse({ email });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await backendFetch<void>("/api/v1/auth/verify-email/resend", {
      method: "POST",
      json: parsed.data,
      headers: forwardedClientHeaders(await headers()),
    });
  } catch (error) {
    return { error: describe(error), values };
  }
  return {
    success: "If that address belongs to an account waiting for confirmation, a new code and link are on their way. Check your inbox and spam folder.",
    values,
  };
}

/** Confirms an email address with the 6-digit code from the verification email, then sends the user to sign in. */
export async function verifyCodeAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  const values = { email };
  const parsed = verifyCodeSchema.safeParse({ email, code: formData.get("code") ?? "" });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await backendFetch<void>("/api/v1/auth/verify-email/code", {
      method: "POST",
      json: parsed.data,
      headers: forwardedClientHeaders(await headers()),
    });
  } catch (error) {
    // The backend says whether the code was wrong or the email has used up its attempts.
    if (error instanceof BackendError && error.apiError?.code === "INVALID_VERIFICATION_CODE" && error.apiError.message) {
      return { fieldErrors: { code: `${error.apiError.message}.` }, values };
    }
    return { error: describe(error), values };
  }
  redirect("/login?verified=1");
}

/**
 * Forgot password, step 1: emails a reset code. The answer is the same whether or not the address is registered,
 * so the user is always taken on to enter the code.
 */
export async function requestPasswordResetAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  const values = { email };
  const parsed = forgotPasswordSchema.safeParse({ email });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await backendFetch<void>("/api/v1/auth/password-reset/request", {
      method: "POST",
      json: parsed.data,
      headers: forwardedClientHeaders(await headers()),
    });
  } catch (error) {
    return { error: describe(error), values };
  }
  if (formData.get("stay") === "1") {
    return { success: "If that address belongs to an account, a new code is on its way. Check your inbox and spam folder.", values };
  }
  redirect(`/reset-password?email=${encodeURIComponent(parsed.data.email)}`);
}

/** Forgot password, step 2: the emailed code and a new password; then the user signs in with it. */
export async function resetPasswordAction(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  const values = { email };
  const parsed = resetPasswordSchema.safeParse({
    email,
    code: formData.get("code") ?? "",
    newPassword: formData.get("newPassword") ?? "",
    confirmPassword: formData.get("confirmPassword") ?? "",
  });
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values };
  try {
    await backendFetch<void>("/api/v1/auth/password-reset/confirm", {
      method: "POST",
      json: { email: parsed.data.email, code: parsed.data.code, newPassword: parsed.data.newPassword },
      headers: forwardedClientHeaders(await headers()),
    });
  } catch (error) {
    if (error instanceof BackendError && error.apiError?.code === "INVALID_RESET_CODE" && error.apiError.message) {
      return { fieldErrors: { code: `${error.apiError.message}.` }, values };
    }
    if (error instanceof BackendError && error.apiError?.fieldErrors?.length) {
      return { fieldErrors: Object.fromEntries(error.apiError.fieldErrors.map((f) => [f.field, f.message])), values };
    }
    if (error instanceof BackendError && error.apiError?.code === "VALIDATION_FAILED" && error.apiError.message) {
      return { fieldErrors: { newPassword: error.apiError.message }, values };
    }
    return { error: describe(error), values };
  }
  redirect("/login?reset=1");
}

/** Confirms an email address with the token from the emailed link. */
export async function verifyEmailToken(token: string): Promise<{ ok: true } | { ok: false; message: string }> {
  try {
    await backendFetch<void>("/api/v1/auth/verify-email", { method: "POST", json: { token } });
    return { ok: true };
  } catch (error) {
    if (error instanceof BackendError && (error.apiError?.code === "INVALID_VERIFICATION_TOKEN" || error.status === 400)) {
      return { ok: false, message: "This link is invalid or has expired. Request a new one below." };
    }
    return { ok: false, message: describe(error) };
  }
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
      // Only a path in Settings: never an open redirect.
      const returnTo = String(formData.get("returnTo") ?? "");
      if (returnTo.startsWith("/settings/")) destination = returnTo;
    } catch {
      destination = "/login";
    }
  }
  redirect(destination);
}
