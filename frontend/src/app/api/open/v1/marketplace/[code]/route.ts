import { openJson, openPreflight, withPhotoUrls } from "@/lib/server/routes/open-api";

/** One listing by its code, with its description and photos. */
export async function GET(_request: Request, ctx: RouteContext<"/api/open/v1/marketplace/[code]">) {
  const { code } = await ctx.params;
  return openJson(`/api/v1/open/marketplace/${encodeURIComponent(code)}`, withPhotoUrls);
}

export const OPTIONS = openPreflight;
