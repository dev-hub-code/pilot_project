import { z } from "zod";

/** Mirrors backend bounds (RegisterRequest / PasswordPolicy); the backend remains authoritative. */
export const PASSWORD_MIN = 12;
export const PASSWORD_MAX = 128;

export const loginSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
  password: z.string().min(1, "Enter your password").max(PASSWORD_MAX),
});

export const registerSchema = z
  .object({
    firstName: z.string().trim().min(1, "Enter your first name").max(100),
    lastName: z.string().trim().min(1, "Enter your last name").max(100),
    email: z.email("Enter a valid email address").max(254),
    password: z
      .string()
      .min(PASSWORD_MIN, `Use at least ${PASSWORD_MIN} characters`)
      .max(PASSWORD_MAX, `Use at most ${PASSWORD_MAX} characters`),
    confirmPassword: z.string(),
    referralCode: z.string().trim().toUpperCase()
      .regex(/^([2-9A-HJ-NP-Z]{8})?$/, "Referral codes are 8 letters and digits"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });

export type { FieldErrors, FormState } from "./form-state";
export { firstErrors } from "./form-state";
