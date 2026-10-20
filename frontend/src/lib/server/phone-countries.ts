import "server-only";
import type { Country } from "@/types/location";
import { backendFetch } from "./backend-client";

/** India alone when the backend cannot be reached, so forms with a phone number still render. */
const FALLBACK: Country[] = [{ code: "IN", name: "India", phoneCode: "91" }];

/** The countries with their dialling codes (from the backend's countries table), for phone number fields. */
export async function getPhoneCountries(): Promise<Country[]> {
  try {
    const countries = await backendFetch<Country[]>("/api/v1/public/locations/countries");
    return countries.filter((c) => c.phoneCode);
  } catch {
    return FALLBACK;
  }
}
