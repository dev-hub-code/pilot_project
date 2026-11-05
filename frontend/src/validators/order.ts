import { z } from "zod";

/** Client-side mirrors of the backend's checkout and payment validation; the backend is authoritative. */
const amount = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

/** Whole units of a plan; the plan's minimum is checked by the backend. */
export const unitsSchema = z.coerce.number({ message: "Enter the number of units" }).int("Enter whole units")
  .min(1, "Buy at least 1 unit").max(100_000_000, "Enter fewer units");

export const idempotencyKeySchema = z.string().regex(/^[A-Za-z0-9_-]{8,100}$/);

export const confirmTransferSchema = z.object({
  externalReference: z.string().trim().min(1, "Enter the bank's reference").max(100),
  amountReceived: amount,
});

const reference = (message: string) =>
  z.string().trim().min(3, message).max(100).regex(/^[A-Za-z0-9/ -]+$/, "Use letters, digits, spaces, dashes or slashes only");

/** Paying from the available balance is confirmed with the investor's password, as a withdrawal is. */
export const payFromBalanceSchema = z.object({
  password: z.string().min(1, "Enter your password to confirm").max(200),
});

export const depositSchema = z.object({
  companyBankAccountId: z.uuid("Choose the account you paid into"),
  mode: z.enum(["ONLINE", "CHEQUE"], { message: "Choose how you paid" }),
  reference: reference("Enter the transaction ID or cheque number"),
});

export const receiptSchema = z.object({
  receiptNumber: reference("Enter the receipt number"),
});

export const rejectPaymentSchema = z.object({
  reason: z.string().trim().min(3, "Give a reason the investor will understand").max(500),
});

export const companyBankAccountSchema = z.object({
  accountName: z.string().trim().min(1, "Enter the account holder name").max(140),
  bankName: z.string().trim().min(1, "Enter the bank's name").max(140),
  branch: z.string().trim().max(140).optional().transform((v) => v || null),
  accountNumber: z.string().transform((v) => v.replace(/\s/g, "")).pipe(z.string().regex(/^\d{6,18}$/, "Enter 6-18 digits")),
  ifscCode: z.string().trim().toUpperCase().regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, "Enter a valid IFSC, e.g. HDFC0001234"),
  upiId: z.string().trim().max(100).optional()
    .refine((v) => !v || /^[A-Za-z0-9._-]{2,256}@[A-Za-z]{2,64}$/.test(v), "Enter a UPI ID like name@bank")
    .transform((v) => v || null),
});

export const refundSchema = z.object({
  reference: z.string().trim().min(1, "Enter the refund transfer's reference").max(100),
  reason: z.string().trim().min(3, "Give a reason").max(500),
});

export const resolveUnmatchedSchema = z.object({
  reason: z.string().trim().min(1, "Say how it was resolved, e.g. the refund reference").max(500),
});
