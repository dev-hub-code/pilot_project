import { NextResponse } from "next/server";
import { authFetch } from "@/lib/server/auth/session";
import type { ArticleSummary, KbPage } from "@/types/support";

/** Up to three published help articles matching what the customer is typing (signed-in customers only). */
export async function GET(request: Request) {
  const q = new URL(request.url).searchParams.get("q")?.trim().slice(0, 200) ?? "";
  if (q.length < 4) return NextResponse.json([]);
  try {
    const page = await authFetch<KbPage<ArticleSummary>>(`/api/v1/support/knowledge-base/articles?${new URLSearchParams({ q, size: "3" })}`);
    return NextResponse.json(page.content);
  } catch {
    return NextResponse.json([]);
  }
}
