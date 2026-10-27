import { z } from "zod";
import { phoneCountry, validPhone } from "./phone";
import { latestBirthDate, parseDayMonthYear } from "@/utils/format";

/** Client-side mirrors of backend validation; the backend remains authoritative. */
const optional = (schema: z.ZodType<string>) =>
  z.preprocess((value) => (typeof value === "string" && value.trim() === "" ? undefined : value), schema.optional());
/** Nationality and address country when none is chosen. */
export const DEFAULT_COUNTRY = "IN";
const country = z.string().regex(/^[A-Z]{2}$/, "Select a country");
/** A date entered as DD/MM/YYYY, sent on as YYYY-MM-DD. */
const dayMonthYear = z.string().transform((value, ctx) => {
  const iso = parseDayMonthYear(value);
  if (!iso) {
    ctx.addIssue({ code: "custom", message: "Enter the date as DD/MM/YYYY, e.g. 15/06/1985" });
    return z.NEVER;
  }
  // YYYY-MM-DD compares as text; the backend also refuses dates not in the past.
  if (iso >= latestBirthDate()) {
    ctx.addIssue({ code: "custom", message: "Enter a date of birth in the past" });
    return z.NEVER;
  }
  return iso;
});

export const profileSchema = z.object({
  firstName: z.string().trim().min(1, "Enter your first name").max(100),
  lastName: z.string().trim().min(1, "Enter your last name").max(100),
  phone: optional(z.string().trim().max(30)),
  phoneCountry,
  dateOfBirth: optional(dayMonthYear),
  nationality: optional(country).transform((v) => v ?? DEFAULT_COUNTRY),
  line1: z.string().trim().min(1, "Enter the first line of your address").max(200),
  line2: optional(z.string().max(200)),
  city: z.string().trim().min(1, "Enter your city").max(100),
  stateRegion: z.string().trim().min(1, "Enter your state").max(100),
  postalCode: z.string().trim().min(1, "Enter your postal code")
    .regex(/^\d{6}$/, "Enter a 6-digit postal code, numbers only"),
  country: optional(country).transform((v) => v ?? DEFAULT_COUNTRY),
}).superRefine(validPhone(true));

export const bankAccountSchema = z.object({
  bankName: z.string().trim().min(1, "Enter the bank's name").max(140),
  country,
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  accountNumber: z.string().trim().regex(/^\d{8,15}$/, "Enter the account number: 8 to 15 digits, numbers only"),
  // IFSC: 4 letters (the bank), a zero, then 6 letters or digits (the branch).
  routingCode: z.string().trim().toUpperCase()
    .regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, "Enter the IFSC code: 4 letters, a zero, then 6 letters or digits (e.g. HDFC0001234)"),
});

/** Aadhaar and PAN are uploaded separately, each with its number. */
export const kycNumberSchema = {
  AADHAAR: z.string().transform((v) => v.replace(/[\s-]/g, ""))
    .pipe(z.string().regex(/^\d{12}$/, "An Aadhaar number has exactly 12 digits")),
  PAN: z.string().transform((v) => v.replace(/\s/g, "").toUpperCase())
    .pipe(z.string().regex(/^[A-Z]{5}\d{4}[A-Z]$/, "Enter your PAN, e.g. ABCDE1234F")),
} as const;

export const reasonSchema = z.object({
  reason: z.string().trim().min(3, "Give a reason (it is recorded in the audit log)").max(500),
});

/** Must match the backend's app.documents.max-file-size. */
export const MAX_FILE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_FILE_TYPES = "application/pdf,image/jpeg,image/png";
