import "server-only";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ContainerDetail } from "@/types/marketplace";
import type { ContainerOption } from "./product-form";

/** Containers that can back a new offering (anything not retired). */
export async function containerOptions(): Promise<ContainerOption[]> {
  const page = await authFetch<PageResponse<ContainerDetail>>("/api/v1/admin/containers?size=100&sort=createdAt,desc");
  return page.content
    .filter(({ container }) => container.status !== "RETIRED")
    .map(({ container: c }) => ({ value: c.id, label: `${c.containerNumber} · ${CONTAINER_TYPE_LABEL[c.containerType]} · ${c.currentLocation}` }));
}
