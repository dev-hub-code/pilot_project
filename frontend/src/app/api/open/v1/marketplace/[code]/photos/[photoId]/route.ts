import { openImage, openPreflight } from "@/lib/server/routes/open-api";

/** A listing's photo. */
export async function GET(_request: Request, ctx: RouteContext<"/api/open/v1/marketplace/[code]/photos/[photoId]">) {
  const { code, photoId } = await ctx.params;
  return openImage(`/api/v1/open/marketplace/${encodeURIComponent(code)}/photos/${encodeURIComponent(photoId)}`);
}

export const OPTIONS = openPreflight;
