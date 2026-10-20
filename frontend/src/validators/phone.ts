import { z } from "zod";
import { DEFAULT_PHONE_COUNTRY, phoneProblem } from "@/lib/phone";

/** The country a phone number was typed in (the field's country code); India when none is chosen. */
export const phoneCountry = z.string().trim().optional()
  .transform((v) => v || DEFAULT_PHONE_COUNTRY)
  .pipe(z.string().regex(/^[A-Z]{2}$/, "Select a country code"));

/** Checks a form's {@code phone} against its {@code phoneCountry}; {@code mobile} accepts mobile numbers only. */
export const validPhone = (mobile: boolean) =>
  (data: { phone?: string; phoneCountry: string }, ctx: z.RefinementCtx) => {
    if (!data.phone) return;
    const problem = phoneProblem(data.phone, data.phoneCountry, mobile);
    if (problem) ctx.addIssue({ code: "custom", path: ["phone"], message: problem });
  };
