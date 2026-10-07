import { z } from "zod";

/** Client-side mirrors of the backend's ticket validation; the backend is authoritative. */
export const MAX_ATTACHMENTS = 3;

export const openTicketSchema = z.object({
  subject: z.string().trim().min(3, "Give your question a short subject").max(200),
  category: z.enum(["ACCOUNT", "INVESTMENT", "PAYMENT", "EARNINGS", "WITHDRAWAL", "REFERRAL", "OTHER"]),
  related: z.string().regex(/^((ORDER|WITHDRAWAL|HOLDING):[0-9a-f-]{36})?$/i).optional(),
  message: z.string().trim().min(1, "Describe what you need").max(8000),
});

export const replySchema = z.object({
  body: z.string().trim().min(1, "Write a message").max(8000),
});
