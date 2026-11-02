import { z } from "zod";

/** Client-side mirror of the backend's checks. */
export const announcementSchema = z.object({
  title: z.string().trim().min(1, "Give the announcement a title").max(200, "At most 200 characters"),
  body: z.string().trim().min(1, "Write the message").max(1000, "At most 1,000 characters"),
  link: z.string().trim().max(300, "At most 300 characters")
    .refine((v) => v === "" || /^\/(?![/\\])[^\s\\]*$/.test(v), "A page of this site, starting with / (e.g. /invest)")
    .transform((v) => (v === "" ? undefined : v)),
  audience: z.enum(["EVERYONE", "INVESTORS", "ONLINE_INVESTORS", "NETWORK_INVESTORS", "STAFF"], { message: "Choose who receives it" }),
});
