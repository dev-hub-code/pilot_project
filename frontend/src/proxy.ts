import { type NextRequest, NextResponse } from "next/server";
import { hasStaffAccess, isInvestor } from "@/lib/permissions";
import { BackendError, backendFetch } from "@/lib/server/backend-client";
import { type AuthTokens, cookieNames, writeSessionCookies } from "@/lib/server/auth/cookies";
import { expiresWithin, verifyAccessToken } from "@/lib/server/auth/tokens";
import { forwardedClientHeaders } from "@/lib/server/request-context";

/** Refresh this long before expiry so bursts of requests rarely race on an expired token. */
const REFRESH_AHEAD_MS = 60_000;

const PROTECTED_PREFIXES = ["/dashboard", "/profile", "/invest", "/orders", "/portfolio", "/earnings", "/referrals", "/withdrawals", "/support", "/notifications", "/statements", "/settings", "/platform"];
const STAFF_PREFIXES = ["/platform"];
/** The investor portal; staff accounts are never investors and are sent to the admin area instead. */
const INVESTOR_PREFIXES = ["/dashboard", "/portfolio", "/earnings", "/referrals", "/withdrawals", "/support", "/orders", "/statements", "/profile/verification", "/profile/bank-accounts"];
/** The staff sign-in; it sits inside the staff area but is open to everyone signed out. */
const STAFF_LOGIN = "/platform/login";
const GUEST_ONLY = ["/login", "/register", STAFF_LOGIN];
const RETIRED = ["/marketplace", "/cart"];
/** The staff console moved from /admin to /platform: old links and bookmarks still work. */
const OLD_STAFF_AREA = "/admin";
/** Route handlers that send their own, stricter CSP (sandboxed file downloads). */
const OWN_CSP = [
  /^\/platform\/kyc\/[^/]+\/documents\/[^/]+$/,
  /^\/platform\/bank-accounts\/[^/]+\/proof$/,
  /^\/platform\/withdrawals\/batches\/[^/]+\/file$/,
  /^\/(?:platform\/)?support\/[^/]+\/attachments\/[^/]+$/,
  /^\/(?:platform\/reports|statements)\/download$/,
  /^\/(?:platform\/)?orders\/[^/]+\/invoice\/pdf$/,
];

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

  const { pathname, search } = request.nextUrl;

  let renewed: AuthTokens | null = null;
  // Not while signing out: the logout route revokes the refresh token the browser sent.
  if (refresh && pathname !== "/logout" && (!access || expiresWithin(access, REFRESH_AHEAD_MS))) {
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

  const staffLogin = pathname === STAFF_LOGIN;

  let response: NextResponse;
  if (matches(pathname, [OLD_STAFF_AREA])) {
    const moved = new URL("/platform" + pathname.slice(OLD_STAFF_AREA.length), request.url);
    moved.search = search;
    response = NextResponse.redirect(moved, 308);
  } else if (matches(pathname, RETIRED)) {
    // The marketplace and cart are gone: old links and bookmarks land on the plans.
    response = NextResponse.redirect(new URL("/invest", request.url));
  } else if (!session && pathname === "/invest") {
    // Visitors see the public, read-only plans; signing in from there leads to the full page.
    response = NextResponse.redirect(new URL("/plans", request.url));
  } else if (session && pathname === "/plans") {
    // Signed in, investors buy from the full page.
    const full = new URL("/invest", request.url);
    full.search = search;
    response = NextResponse.redirect(full);
  } else if (!session && !staffLogin && matches(pathname, PROTECTED_PREFIXES)) {
    // Staff sign in on their own page.
    const login = new URL(matches(pathname, STAFF_PREFIXES) ? STAFF_LOGIN : "/login", request.url);
    login.searchParams.set("next", pathname + search);
    response = NextResponse.redirect(login);
  } else if (session?.passwordChangeRequired && !staffLogin && matches(pathname, PROTECTED_PREFIXES)) {
    // UX only: the API itself refuses everything but the password change for such a session.
    response = NextResponse.redirect(new URL("/change-password", request.url));
  } else if (!session && pathname === "/change-password") {
    response = NextResponse.redirect(new URL("/login", request.url));
  } else if (session && matches(pathname, GUEST_ONLY)) {
    response = NextResponse.redirect(new URL(isInvestor(session.permissions) ? "/dashboard" : "/platform", request.url));
  } else if (session && matches(pathname, STAFF_PREFIXES) && !hasStaffAccess(session.permissions)) {
    response = NextResponse.redirect(new URL("/dashboard", request.url));
  } else if (session && !isInvestor(session.permissions) && hasStaffAccess(session.permissions)
    && matches(pathname, INVESTOR_PREFIXES)) {
    response = NextResponse.redirect(new URL("/platform", request.url));
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
  // Signed-in pages are never kept by the browser or a proxy, so none can be shown again after signing out.
  if (session || (matches(pathname, PROTECTED_PREFIXES) && !staffLogin)) {
    response.headers.set("Cache-Control", "private, no-store, max-age=0");
  }
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
    img-src 'self' blob: data: https://cdn.razorpay.com https://upload.wikimedia.org;
    font-src 'self';
    connect-src 'self' https://api.razorpay.com https://lumberjack.razorpay.com;
    frame-src https://api.razorpay.com https://checkout.razorpay.com;
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
