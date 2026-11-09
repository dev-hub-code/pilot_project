import { NextResponse } from "next/server";
import { authFetch } from "@/lib/server/auth/session";
import type { CustomerOption } from "@/types/support";

/** Customers matching a name, email or customer code; the backend checks the caller may open tickets. */
export async function GET(request: Request) {
  const q = new URL(request.url).searchParams.get("q")?.trim().slice(0, 100) ?? "";
  if (q.length < 2) return NextResponse.json([]);
  try {
    return NextResponse.json(await authFetch<CustomerOption[]>(`/api/v1/admin/support/customers?${new URLSearchParams({ q })}`));
  } catch {
    return NextResponse.json([], { status: 403 });
  }
}
