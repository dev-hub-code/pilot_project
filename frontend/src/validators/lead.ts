import { z } from "zod";

/** Client-side mirrors of the backend's lead validation; the backend is authoritative. */
const optional = (max: number) => z.string().trim().max(max).optional().transform((v) => v || undefined);
const phone = z.string().trim().optional().transform((v) => v || undefined)
  .pipe(z.string().regex(/^[+0-9 ()-]{6,40}$/, "Enter a phone number").optional());
const country = z.string().trim().optional().transform((v) => v || undefined)
  .pipe(z.string().regex(/^[A-Z]{2}$/).optional());
const interest = z.enum(["RETAIL", "HNI", "UNSURE"]);

export const interestSchema = z.object({
  firstName: z.string().trim().min(1, "Enter your first name").max(100),
  lastName: optional(100),
  email: z.email("Enter a valid email address").max(254),
  phone,
  country,
  interest,
  message: optional(2000),
  consent: z.literal("on", { message: "Please agree so we can contact you" }),
  website: z.string().max(200).optional(),
});

export const leadSchema = z.object({
  firstName: z.string().trim().min(1, "Enter a first name").max(100),
  lastName: optional(100),
  email: z.string().trim().optional().transform((v) => v || undefined).pipe(z.email("Enter a valid email").max(254).optional()),
  phone,
  country,
  interest,
  estimatedAmount: z.string().trim().optional().transform((v) => v || undefined)
    .pipe(z.string().regex(/^\d{1,13}(\.\d{1,2})?$/, "Up to 2 decimals").optional()),
  estimatedCurrency: z.string().trim().optional().transform((v) => v || undefined)
    .pipe(z.string().regex(/^[A-Z]{3}$/).optional()),
  /** datetime-local input, read as UTC. */
  nextFollowUpAt: z.string().trim().optional()
    .transform((v) => (v ? `${v.length === 16 ? `${v}:00` : v}Z` : undefined))
    .pipe(z.iso.datetime("Enter a valid date and time").optional()),
  ownerId: z.string().trim().optional().transform((v) => v || undefined).pipe(z.uuid().optional()),
}).refine((d) => d.email || d.phone, { path: ["email"], message: "Give an email address or a phone number" })
  .refine((d) => !d.estimatedAmount === !d.estimatedCurrency, { path: ["estimatedAmount"], message: "Give the amount and its currency" });

export const stageSchema = z.object({
  stage: z.enum(["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "WON", "LOST"]),
  reason: optional(500),
}).refine((d) => d.stage !== "LOST" || d.reason, { path: ["reason"], message: "Say why the lead was lost" });

export const activitySchema = z.object({
  type: z.enum(["NOTE", "CALL", "EMAIL", "MEETING"]),
  body: z.string().trim().min(1, "Write something").max(4000),
});
