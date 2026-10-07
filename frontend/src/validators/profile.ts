import { z } from "zod";

/** Client-side mirrors of backend validation; the backend remains authoritative. */
const optional = (schema: z.ZodType<string>) =>
  z.preprocess((value) => (typeof value === "string" && value.trim() === "" ? undefined : value), schema.optional());
const country = z.string().regex(/^[A-Z]{2}$/, "Select a country");

export const profileSchema = z.object({
  firstName: z.string().trim().min(1, "Enter your first name").max(100),
  lastName: z.string().trim().min(1, "Enter your last name").max(100),
  phone: optional(z.string().regex(/^\+[1-9]\d{6,14}$/, "Use international format, e.g. +14155550123")),
  dateOfBirth: optional(z.iso.date("Enter a valid date")),
  nationality: optional(country),
  line1: optional(z.string().max(200)),
  line2: optional(z.string().max(200)),
  city: optional(z.string().max(100)),
  stateRegion: optional(z.string().max(100)),
  postalCode: optional(z.string().max(20)),
  country: optional(country),
  emailNotifications: z.boolean(),
  smsNotifications: z.boolean(),
});

export const taxSchema = z.object({
  taxResidencyCountry: country,
  taxId: z.string().trim().regex(/^[A-Za-z0-9 -]{4,30}$/, "4–30 letters, digits, spaces or dashes"),
});

export const bankAccountSchema = z.object({
  accountHolderName: z.string().trim().min(1, "Enter the account holder's name").max(140),
  bankName: z.string().trim().min(1, "Enter the bank's name").max(140),
  country,
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  accountNumber: z.string().trim().regex(/^[A-Za-z0-9 -]{4,40}$/, "Enter the account number or IBAN"),
  routingCode: z.string().trim().regex(/^[A-Za-z0-9 -]{4,20}$/, "Enter the SWIFT/BIC, IFSC, routing or sort code"),
});

/** Today as YYYY-MM-DD (UTC), comparable with ISO date strings. */
const today = () => new Date().toISOString().slice(0, 10);

export const kycSchema = z.object({
  legalFirstName: z.string().trim().min(1, "As shown on your document").max(100),
  legalLastName: z.string().trim().min(1, "As shown on your document").max(100),
  dateOfBirth: z.iso.date("Enter a valid date").refine((d) => d < today(), "Must be in the past"),
  nationality: country,
  documentType: z.enum(["PASSPORT", "NATIONAL_ID", "DRIVING_LICENSE"], "Select a document type"),
  documentNumber: z.string().trim().regex(/^[A-Za-z0-9 -]{4,30}$/, "4–30 letters, digits, spaces or dashes"),
  documentIssuingCountry: country,
  documentExpiryDate: z.iso.date("Enter a valid date").refine((d) => d > today(), "This document has expired"),
});

export const reasonSchema = z.object({
  reason: z.string().trim().min(3, "Give a reason (it is recorded in the audit log)").max(500),
});

/** Must match the backend's app.documents.max-file-size. */
export const MAX_FILE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_FILE_TYPES = "application/pdf,image/jpeg,image/png";
