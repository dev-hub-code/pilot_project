import "server-only";
import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import { hasStaffAccess } from "@/lib/permissions";
import { BackendError, backendFetch, backendRequest, type BackendRequestInit } from "../backend-client";
import { forwardedClientHeaders } from "../request-context";
import { cookieNames } from "./cookies";
import { type AccessTokenClaims, verifyAccessToken } from "./tokens";

export type Session = AccessTokenClaims;

/** Session of the current request, verified once per render. */
export const getSession = cache(async (): Promise<Session | null> => {
  const token = (await cookies()).get(cookieNames().access)?.value;
  return token ? verifyAccessToken(token) : null;
});

export async function requireSession(): Promise<Session> {
  const session = await getSession();
  if (!session) redirect("/login");
  return session;
}

/** UX guard for staff areas. The backend enforces the real permission on every API call. */
export async function requireStaff(): Promise<Session> {
  const session = await requireSession();
  if (!hasStaffAccess(session.permissions)) redirect("/dashboard");
  return session;
}

/**
 * Calls the backend as the signed-in user. A 401 means the session was revoked server-side
 * (logout elsewhere, password change, role change), so the user is sent to sign in again.
 */
export async function authFetch<T>(path: string, init: BackendRequestInit = {}): Promise<T> {
  const headersWithAuth = await authorizedHeaders(init.headers);
  try {
    return await backendFetch<T>(path, { ...init, headers: headersWithAuth });
  } catch (error) {
    if (error instanceof BackendError && error.status === 401) redirect("/session-expired");
    throw error;
  }
}

/** Raw authenticated response (file downloads). */
export async function authRequest(path: string, init: BackendRequestInit = {}): Promise<Response> {
  const headersWithAuth = await authorizedHeaders(init.headers);
  try {
    return await backendRequest(path, { ...init, headers: headersWithAuth });
  } catch (error) {
    if (error instanceof BackendError && error.status === 401) redirect("/session-expired");
    throw error;
  }
}

async function authorizedHeaders(base: HeadersInit | undefined): Promise<Headers> {
  const token = (await cookies()).get(cookieNames().access)?.value;
  if (!token) redirect("/login");
  const requestHeaders = new Headers(base);
  requestHeaders.set("Authorization", `Bearer ${token}`);
  for (const [name, value] of Object.entries(forwardedClientHeaders(await headers()))) {
    requestHeaders.set(name, value);
  }
  return requestHeaders;
}
