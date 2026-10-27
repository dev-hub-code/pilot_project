import { NextResponse } from "next/server";
import { rememberEmailPrompt } from "@/lib/server/auth/cookies";
import { getSession } from "@/lib/server/auth/session";

/**
 * The dashboard's "confirm your email" reminder was shown: not again in this session. A route rather than a Server
 * Action, whose cookie change would re-render the dashboard and close the reminder at once.
 */
export async function POST() {
  const session = await getSession();
  if (!session) return new NextResponse(null, { status: 401 });
  const response = new NextResponse(null, { status: 204, headers: { "Cache-Control": "no-store" } });
  rememberEmailPrompt(response.cookies, session.sessionId);
  return response;
}
