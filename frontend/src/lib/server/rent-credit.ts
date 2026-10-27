import "server-only";
import { backendFetch } from "./backend-client";

/** How often rent is credited, for wording such as "credited to your wallet every hour". */
export interface RentCredit {
  intervalMinutes: number;
  /** e.g. "every hour", "every 6 hours", "every day". */
  label: string;
}

/**
 * The interval admins have set. If the backend cannot be reached, pages still render with general wording ("regularly")
 * rather than a guessed interval that may contradict what admins configured.
 */
export async function getRentCredit(): Promise<RentCredit | null> {
  try {
    return await backendFetch<RentCredit>("/api/v1/public/rent-credit");
  } catch {
    return null;
  }
}

/** For wording: "every 6 hours", or "regularly" when the interval is not known. */
export function creditLabel(credit: RentCredit | null): string {
  return credit?.label ?? "regularly";
}
