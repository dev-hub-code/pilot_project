import { type NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/server/backend-client";
import { clearSessionCookies, cookieNames } from "@/lib/server/auth/cookies";

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

  const refreshToken = request.cookies.get(cookieNames().refresh)?.value;
  if (refreshToken) {
    try {
      await backendFetch<void>("/api/v1/auth/logout", { method: "POST", json: { refreshToken } });
    } catch (error) {
      // Cookies are cleared regardless; the session still expires server-side.
      console.error("Backend logout failed", error);
    }
  }

  // 303: the browser follows with a GET to /login, not a repeat of this POST. A relative location keeps the
  // scheme and host the browser used (https behind the proxy).
  const response = new NextResponse(null, {
    status: 303,
    headers: { Location: "/login", "Clear-Site-Data": '"cache", "storage"', "Cache-Control": "no-store" },
  });
  clearSessionCookies(response.cookies);
  return response;
}
