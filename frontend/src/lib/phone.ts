import { AsYouType, type CountryCode, parsePhoneNumberFromString } from "libphonenumber-js/max";

/**
 * Phone numbers, checked as the backend checks them (libphonenumber there, libphonenumber-js here): a number typed
 * nationally is read in its chosen country, one typed with "+" and a country code may belong to another.
 */

/** The country a number is read in when none is chosen. */
export const DEFAULT_PHONE_COUNTRY = "IN";
export const MOBILE_MESSAGE = "Enter a valid mobile number for the selected country";
export const PHONE_MESSAGE = "Enter a valid phone number for the selected country";

function parse(number: string, country: string | undefined) {
  try {
    return parsePhoneNumberFromString(number, (country || DEFAULT_PHONE_COUNTRY) as CountryCode);
  } catch {
    return undefined; // an unknown country
  }
}

/** Why the number is not acceptable, or null when it is; {@code mobile} accepts mobile numbers only. */
export function phoneProblem(number: string, country: string | undefined, mobile: boolean): string | null {
  const parsed = parse(number, country);
  if (!parsed?.isValid()) return mobile ? MOBILE_MESSAGE : PHONE_MESSAGE;
  const type = parsed.getType();
  if (mobile && type !== "MOBILE" && type !== "FIXED_LINE_OR_MOBILE") return MOBILE_MESSAGE;
  return null;
}

/** The country a valid number belongs to (e.g. "GB" for "+44 7400 123456"), if it can tell. */
export function phoneCountryOf(number: string, country: string | undefined): string | undefined {
  const parsed = parse(number, country);
  return parsed?.isValid() ? parsed.country : undefined;
}

/**
 * A stored number as the form shows it: its country and the number without the country code
 * ("+919876543210", "IN" → "IN", "98765 43210"). A number saved before countries were asked is shown as it was.
 */
export function splitPhone(stored: string | null | undefined, country: string | null | undefined) {
  if (!stored) return { country: country || DEFAULT_PHONE_COUNTRY, national: "" };
  const parsed = stored.startsWith("+") ? parse(stored, undefined) : undefined;
  if (!parsed) return { country: country || DEFAULT_PHONE_COUNTRY, national: stored };
  return {
    country: country || parsed.country || DEFAULT_PHONE_COUNTRY,
    national: parsed.formatNational().replace(/^0(?=\d)/, ""),
  };
}

/** A stored number for reading: "+919876543210" → "+91 98765 43210"; anything else (masked, legacy) as it is. */
export function formatPhone(stored: string): string {
  const parsed = stored.startsWith("+") ? parse(stored, undefined) : undefined;
  return parsed ? parsed.formatInternational() : stored;
}

/**
 * A number as it is being typed, laid out the way its country writes it ("98765 43210" in India, "07400 123456" in
 * the UK, "+971 50 123 4567" when typed with its country code). Only digits and a leading "+" are kept.
 */
export function formatPhoneInput(value: string, country: string): string {
  const cleaned = value.replace(/[^\d+]/g, "").replace(/(?!^)\+/g, "");
  if (!cleaned) return "";
  try {
    return new AsYouType((country || DEFAULT_PHONE_COUNTRY) as CountryCode).input(cleaned);
  } catch {
    return cleaned;
  }
}
