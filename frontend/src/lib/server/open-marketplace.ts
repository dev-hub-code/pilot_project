import { headers } from "next/headers";
import { backendFetch } from "@/lib/server/backend-client";
import { forwardedClientHeaders } from "@/lib/server/request-context";
import type { RentTier } from "@/types/marketplace";
import type { OpenListing } from "@/types/open";
import type { PageResponse } from "@/types/api";

/**
 * The public marketplace's data, from the backend's open API: what anyone may see without signing in. The visitor's
 * address is passed on, so its per-visitor limit applies to them.
 */
async function open<T>(path: string): Promise<T> {
  return backendFetch<T>(`/api/v1/open${path}`, { headers: forwardedClientHeaders(await headers()) });
}

export function openListings(query: URLSearchParams): Promise<PageResponse<OpenListing>> {
  return open(`/marketplace?${query}`);
}

export function openListing(code: string): Promise<OpenListing> {
  return open(`/marketplace/${encodeURIComponent(code)}`);
}

export function openRentBands(): Promise<RentTier[]> {
  return open("/rent-bands");
}

/** A listing's photo, served by the open API's route on this site. */
export function openPhotoSrc(code: string, photoId: string): string {
  return `/api/open/v1/marketplace/${encodeURIComponent(code)}/photos/${encodeURIComponent(photoId)}`;
}
