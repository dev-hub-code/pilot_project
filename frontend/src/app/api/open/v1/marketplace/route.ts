import type { NextRequest } from "next/server";
import { openJson, openPreflight, withPhotoUrls } from "@/lib/server/routes/open-api";

const PARAMS = ["status", "containerType", "sort", "page", "size"];

/** Listed containers, a page at a time. */
export async function GET(request: NextRequest) {
  const query = new URLSearchParams();
  for (const name of PARAMS) {
    const value = request.nextUrl.searchParams.get(name);
    if (value) query.set(name, value);
  }
  return openJson<{ content: Parameters<typeof withPhotoUrls>[0][] }>(`/api/v1/open/marketplace?${query}`,
    (page) => ({ ...page, content: page.content.map(withPhotoUrls) }));
}

export const OPTIONS = openPreflight;
