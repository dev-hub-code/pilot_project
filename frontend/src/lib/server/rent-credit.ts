import "server-only";
import { backendFetch } from "./backend-client";

/** How often rent is credited, for wording such as "credited to your wallet every hour". */
export interface RentCredit {
  intervalMinutes: number;
  /** e.g. "every hour", "every 6 hours", "every day". */
  label: string;
}

const HOURLY: RentCredit = { intervalMinutes: 60, label: "every hour" };

/** The interval admins have set; hourly if the backend cannot be reached, so pages still render. */
export async function getRentCredit(): Promise<RentCredit> {
  try {
    return await backendFetch<RentCredit>("/api/v1/public/rent-credit");
  } catch {
    return HOURLY;
  }
}
