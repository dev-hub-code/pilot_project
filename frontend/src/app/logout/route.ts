import { type NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/server/backend-client";
import { hasStaffAccess } from "@/lib/permissions";
import { clearSessionCookies, cookieNames } from "@/lib/server/auth/cookies";
import { verifyAccessToken } from "@/lib/server/auth/tokens";

/**
 * Sign out, as a full page request (a plain form POST) rather than a Server Action: the browser leaves the page, so
 * the client router's in-memory copies of signed-in pages are gone and Back cannot show them. Clear-Site-Data then
 * drops this site's HTTP cache, back/forward cache and storage (sessionStorage, localStorage) in the browser.
 */
export async function POST(request: NextRequest) {
  // Only this site's own pages may sign the user out (older browsers omit the header).
  const site = request.headers.get("sec-fetch-site");
  if (site && site !== "same-origin") {
    return new NextResponse(null, { status: 403 });
  }

  const access = request.cookies.get(cookieNames().access)?.value;
  const claims = access ? await verifyAccessToken(access) : null;
  // Staff go back to their own sign-in page.
  const signIn = claims && hasStaffAccess(claims.permissions) ? "/platform/login" : "/login";
  const refreshToken = request.cookies.get(cookieNames().refresh)?.value;
  if (refreshToken) {
    try {
      await backendFetch<void>("/api/v1/auth/logout", { method: "POST", json: { refreshToken } });
    } catch (error) {
      // Cookies are cleared regardless; the session still expires server-side.
      console.error("Backend logout failed", error);
    }
  }

  // 303: the browser follows with a GET to the sign-in page, not a repeat of this POST. A relative location keeps the
  // scheme and host the browser used (https behind the proxy).
  const response = new NextResponse(null, {
    status: 303,
    headers: { Location: signIn, "Clear-Site-Data": '"cache", "storage"', "Cache-Control": "no-store" },
  });
  clearSessionCookies(response.cookies);
  return response;
}
