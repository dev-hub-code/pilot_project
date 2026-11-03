import { z } from "zod";
import { phoneCountry, validPhone } from "./phone";

/** A network investor made by an administrator. Client-side mirror; the backend is authoritative. */
export const investorSchema = z.object({
  firstName: z.string().trim().min(1, "Enter a first name").max(100),
  lastName: z.string().trim().min(1, "Enter a last name").max(100),
  email: z.email("Enter a valid email address").max(254),
  phone: z.string().trim().min(1, "Enter their mobile number").max(30),
  phoneCountry,
  referralCode: z.string().trim()
    .regex(/^[^a-z]*$/, "Referral codes are in capital letters")
    .regex(/^([2-9A-HJ-NP-Z]{8})?$/, "Referral codes are 8 capital letters and digits"),
  signupsJoinNetwork: z.boolean(),
}).superRefine(validPhone(true));

/** An invitation any employee sends a client, to open their own network investor account. */
export const inviteInvestorSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
  firstName: z.string().trim().min(1, "Enter their first name").max(100),
  lastName: z.string().trim().min(1, "Enter their last name").max(100),
  signupsJoinNetwork: z.boolean(),
});
