import { z } from "zod";

/** Mirrors the backend's PasswordPolicy (new passwords only); the backend remains authoritative. */
export const PASSWORD_MIN = 6;
export const PASSWORD_MAX = 18;
/** Sign-in accepts passwords set under earlier, longer limits. */
const LOGIN_PASSWORD_MAX = 128;
export const PASSWORD_RULES = `${PASSWORD_MIN} to ${PASSWORD_MAX} characters, with at least one uppercase letter, one number and one special character (e.g. ! @ # $ -)`;

/** A new password: length, letters and numbers (an uppercase letter, a digit) and a special character (anything but a letter, digit or space). */
const newPassword = z
  .string()
  .min(PASSWORD_MIN, `Use at least ${PASSWORD_MIN} characters`)
  .max(PASSWORD_MAX, `Use at most ${PASSWORD_MAX} characters`)
  .regex(/\p{Lu}/u, "Include at least one uppercase letter")
  .regex(/\p{Nd}/u, "Include at least one number")
  .regex(/[^\p{L}\p{N}\s]/u, "Include at least one special character, such as ! @ # $ -");

export const loginSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
  password: z.string().min(1, "Enter your password").max(LOGIN_PASSWORD_MAX),
});

export const resendVerificationSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
});

export const forgotPasswordSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
});

export const resetPasswordSchema = z
  .object({
    email: z.email("Enter a valid email address").max(254),
    code: z.string().trim().regex(/^\d{6}$/, "Enter the 6-digit code from the email"),
    newPassword,
    confirmPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match" });

export const verifyCodeSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
  code: z.string().trim().regex(/^\d{6}$/, "Enter the 6-digit code from the email"),
});

export const registerSchema = z
  .object({
    firstName: z.string().trim().min(1, "Enter your first name").max(100),
    lastName: z.string().trim().min(1, "Enter your last name").max(100),
    email: z.email("Enter a valid email address").max(254),
    phone: z.string().trim().min(1, "Enter your mobile number")
      .regex(/^[+0-9 ()-]{6,20}$/, "Enter a mobile number (digits, spaces, +, brackets or dashes)"),
    password: newPassword,
    confirmPassword: z.string(),
    referralCode: z.string().trim()
      .regex(/^[^a-z]*$/, "Referral codes are in capital letters")
      .regex(/^([2-9A-HJ-NP-Z]{8})?$/, "Referral codes are 8 capital letters and digits"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password").max(LOGIN_PASSWORD_MAX),
    newPassword,
    confirmPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match" })
  .refine((d) => d.newPassword !== d.currentPassword, { path: ["newPassword"], message: "Choose a different password" });

export type { FieldErrors, FormState } from "./form-state";
export { firstErrors } from "./form-state";
