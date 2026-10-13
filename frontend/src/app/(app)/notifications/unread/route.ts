import { NextResponse } from "next/server";
import { authFetch, getSession } from "@/lib/server/auth/session";

/** The signed-in user's unread notification count, polled by the header bell. */
export async function GET() {
  if (!(await getSession())) return NextResponse.json({ unread: 0 }, { status: 401 });
  try {
    const { unread } = await authFetch<{ unread: number }>("/api/v1/notifications/unread-count");
    return NextResponse.json({ unread }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    // A failed poll keeps the badge as it is; the next poll tries again.
    return NextResponse.json({ unread: null }, { status: 502, headers: { "Cache-Control": "no-store" } });
  }
}
