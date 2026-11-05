import type { Holding } from "@/types/order";

/**
 * Where an investor's investments stand, e.g. "1 running · 2 awaiting your acceptance"; "" when they have none.
 * A term starts only once its agreement is accepted, so "running" alone can read 0.
 */
export function holdingBreakdown(holdings: readonly Pick<Holding, "status">[]): string {
  const count = (status: Holding["status"]) => holdings.filter((h) => h.status === status).length;
  return [
    [count("ACTIVE"), "running"],
    [count("AWAITING_ACCEPTANCE"), "awaiting your acceptance"],
    [count("MATURED"), "completed"],
  ].filter(([n]) => Number(n) > 0).map(([n, label]) => `${n} ${label}`).join(" · ");
}

/** Investments the investor still holds: everything except completed leases. */
export function investmentsHeld(holdings: readonly Pick<Holding, "status">[]): number {
  return holdings.filter((h) => h.status !== "MATURED").length;
}
