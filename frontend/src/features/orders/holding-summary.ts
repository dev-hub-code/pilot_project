import type { Holding } from "@/types/order";

/**
 * Where an investor's containers stand, e.g. "1 on lease · 2 awaiting your acceptance"; "" when they own none.
 * Leases start only once a container is assigned and its agreement accepted, so "on lease" alone can read 0.
 */
export function holdingBreakdown(holdings: readonly Pick<Holding, "status">[]): string {
  const count = (status: Holding["status"]) => holdings.filter((h) => h.status === status).length;
  return [
    [count("ACTIVE"), "on lease"],
    [count("AWAITING_ACCEPTANCE"), "awaiting your acceptance"],
    [count("AWAITING_CONTAINER"), "awaiting a container number"],
    [count("MATURED"), "lease completed"],
  ].filter(([n]) => Number(n) > 0).map(([n, label]) => `${n} ${label}`).join(" · ");
}

/** Containers the investor still holds: everything except completed leases. */
export function containersOwned(holdings: readonly Pick<Holding, "status">[]): number {
  return holdings.filter((h) => h.status !== "MATURED").length;
}
