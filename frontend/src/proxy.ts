import { type NextRequest, NextResponse } from "next/server";
import { hasStaffAccess } from "@/lib/permissions";
import { BackendError, backendFetch } from "@/lib/server/backend-client";
import { type AuthTokens, cookieNames, writeSessionCookies } from "@/lib/server/auth/cookies";
import { expiresWithin, verifyAccessToken } from "@/lib/server/auth/tokens";
import { forwardedClientHeaders } from "@/lib/server/request-context";

/** Refresh this long before expiry so bursts of requests rarely race on an expired token. */
const REFRESH_AHEAD_MS = 60_000;

const PROTECTED_PREFIXES = ["/dashboard", "/profile", "/marketplace", "/admin"];
const STAFF_PREFIXES = ["/admin"];
const GUEST_ONLY = ["/login", "/register"];
/** Route handlers that send their own, stricter CSP (sandboxed file downloads). */
const OWN_CSP = [/^\/(?:admin\/kyc|admin\/containers|marketplace)\/[^/]+\/documents\/[^/]+$/];

/**
 * Runs before every page request:
 *  1. Content-Security-Policy with a per-request nonce.
 *  2. Silent session renewal: rotates the refresh token when the access token is missing or about
 *     to expire, updating both the browser cookies and this request's cookies.
 *  3. Optimistic route guards (UX only - the backend authorizes every API call).
 */
export async function proxy(request: NextRequest) {
  const names = cookieNames();
  const access = request.cookies.get(names.access)?.value;
  const refresh = request.cookies.get(names.refresh)?.value;

  let renewed: AuthTokens | null = null;
  if (refresh && (!access || expiresWithin(access, REFRESH_AHEAD_MS))) {
    renewed = await tryRefresh(refresh, request.headers);
    if (renewed) {
      request.cookies.set(names.access, renewed.accessToken);
      request.cookies.set(names.refresh, renewed.refreshToken);
    }
    // On failure cookies are left alone: a concurrent request may have just rotated them, and
    // clearing here could overwrite the fresh ones. Stale cookies simply fail verification.
  }

  const effectiveAccess = renewed?.accessToken ?? access;
  const session = effectiveAccess ? await verifyAccessToken(effectiveAccess) : null;
  const { pathname, search } = request.nextUrl;

  let response: NextResponse;
  if (!session && matches(pathname, PROTECTED_PREFIXES)) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname + search);
    response = NextResponse.redirect(login);
  } else if (session && matches(pathname, STAFF_PREFIXES) && !hasStaffAccess(session.permissions)) {
    response = NextResponse.redirect(new URL("/dashboard", request.url));
  } else if (session && matches(pathname, GUEST_ONLY)) {
    response = NextResponse.redirect(new URL("/dashboard", request.url));
  } else if (OWN_CSP.some((pattern) => pattern.test(pathname))) {
    response = NextResponse.next({ request: { headers: new Headers(request.headers) } });
  } else {
    const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
    const csp = contentSecurityPolicy(nonce);
    const requestHeaders = new Headers(request.headers);
    requestHeaders.set("x-nonce", nonce);
    requestHeaders.set("Content-Security-Policy", csp);
    response = NextResponse.next({ request: { headers: requestHeaders } });
    response.headers.set("Content-Security-Policy", csp);
  }

  if (renewed) writeSessionCookies(response.cookies, renewed);
  return response;
}

async function tryRefresh(refreshToken: string, incoming: Headers): Promise<AuthTokens | null> {
  try {
    return await backendFetch<AuthTokens>("/api/v1/auth/refresh", {
      method: "POST",
      json: { refreshToken },
      headers: forwardedClientHeaders(incoming),
    });
  } catch (error) {
    if (!(error instanceof BackendError)) {
      console.error("Session refresh failed: backend unreachable", error);
    }
    return null;
  }
}

function matches(pathname: string, prefixes: readonly string[]): boolean {
  return prefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/**
 * Production: nonce-based scripts and styles, nothing inline. Development additionally allows
 * eval (React debugging) and inline styles injected by the dev tooling - a nonce would disable
 * 'unsafe-inline', so dev styles are not nonce-restricted.
 */
function contentSecurityPolicy(nonce: string): string {
  const isDev = process.env.NODE_ENV === "development";
  return `
    default-src 'self';
    script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""};
    style-src 'self' ${isDev ? "'unsafe-inline'" : `'nonce-${nonce}'`};
    img-src 'self' blob: data:;
    font-src 'self';
    connect-src 'self';
    object-src 'none';
    base-uri 'self';
    form-action 'self';
    frame-ancestors 'none';
    ${isDev ? "" : "upgrade-insecure-requests;"}
  `
    .replace(/\s{2,}/g, " ")
    .trim();
}

export const config = {
  matcher: [
    // Everything except static assets and Next internals.
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)",
  ],
};
