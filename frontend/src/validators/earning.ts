import { z } from "zod";

/** Client-side mirrors of the backend's ledger validation; the backend is authoritative. */
const amount = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

export const adjustmentSchema = z.object({
  userId: z.uuid("Enter the investor's user id"),
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  direction: z.enum(["CREDIT", "DEBIT"]),
  balance: z.enum(["RENT_BANK", "RENT_CASH", "REFERRAL"], { error: "Choose a balance" }),
  amount,
  reason: z.string().trim().min(3, "Give a reason").max(250),
});
