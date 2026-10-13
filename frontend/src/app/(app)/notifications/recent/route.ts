import { NextResponse } from "next/server";
import { authFetch, getSession } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AppNotification } from "@/types/support";

/** The latest notifications and the unread count, for the header bell's popup. */
export async function GET() {
  if (!(await getSession())) return NextResponse.json(null, { status: 401 });
  try {
    const [page, { unread }] = await Promise.all([
      authFetch<PageResponse<AppNotification>>("/api/v1/notifications?page=0&size=8"),
      authFetch<{ unread: number }>("/api/v1/notifications/unread-count"),
    ]);
    return NextResponse.json({ items: page.content, unread }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json(null, { status: 502, headers: { "Cache-Control": "no-store" } });
  }
}
