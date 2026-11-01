import { openJson, openPreflight } from "@/lib/server/routes/open-api";

/** One plan by its code. */
export async function GET(_request: Request, ctx: RouteContext<"/api/open/v1/plans/[code]">) {
  const { code } = await ctx.params;
  return openJson(`/api/v1/open/plans/${encodeURIComponent(code)}`);
}

export const OPTIONS = openPreflight;
