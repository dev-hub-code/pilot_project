import { z } from "zod";

/** Client-side mirror of the backend's checks; the backend also refuses unknown placeholders. */
export const agreementTemplateSchema = z.object({
  title: z.string().trim().min(1, "Give the agreement a title").max(200, "At most 200 characters"),
  body: z.string().trim().min(1, "Write the agreement's wording").max(50000, "At most 50,000 characters"),
});
