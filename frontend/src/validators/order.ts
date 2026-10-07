import { z } from "zod";

/** Client-side mirrors of the backend's checkout and payment validation; the backend is authoritative. */
const amount = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

/** Whole containers of a plan; mirrors the backend's 1-50 per plan in one order. */
export const cartItemSchema = z.object({
  quantity: z.coerce.number({ message: "Enter how many containers" }).int("Enter a whole number of containers")
    .min(1, "Buy at least one container").max(50, "At most 50 containers per plan in one order"),
});

export const idempotencyKeySchema = z.string().regex(/^[A-Za-z0-9_-]{8,100}$/);

export const confirmTransferSchema = z.object({
  externalReference: z.string().trim().min(1, "Enter the bank's reference").max(100),
  amountReceived: amount,
});

const reference = (message: string) =>
  z.string().trim().min(3, message).max(100).regex(/^[A-Za-z0-9/ -]+$/, "Use letters, digits, spaces, dashes or slashes only");

export const depositSchema = z.object({
  companyBankAccountId: z.uuid("Choose the account you paid into"),
  mode: z.enum(["ONLINE", "CHEQUE", "CASH_DEPOSIT"], { message: "Choose how you paid" }),
  reference: reference("Enter the transaction ID, cheque number or receipt number"),
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
