import { z } from "zod";

/** Client-side mirror of the backend's checks. */
export const termsSchema = z.object({
  title: z.string().trim().min(1, "Give the terms a title").max(200, "At most 200 characters"),
  body: z.string().trim().min(1, "Write the terms").max(100_000, "At most 100,000 characters"),
});
