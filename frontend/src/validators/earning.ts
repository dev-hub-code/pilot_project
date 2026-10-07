import { z } from "zod";

/** Client-side mirrors of the backend's rental and ledger validation; the backend is authoritative. */
const amount = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

export const recordRentalSchema = z.object({
  productId: z.uuid(),
  periodNumber: z.coerce.number().int().min(1, "Choose a period"),
  amount,
  receivedOn: z.iso.date("Enter the date the money arrived"),
  externalReference: z.string().trim().min(1, "Enter the bank's reference").max(100),
  note: z.string().trim().max(500).optional().transform((v) => v || undefined),
});

export const adjustmentSchema = z.object({
  userId: z.uuid("Enter the investor's user id"),
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  direction: z.enum(["CREDIT", "DEBIT"]),
  amount,
  reason: z.string().trim().min(3, "Give a reason").max(250),
});
