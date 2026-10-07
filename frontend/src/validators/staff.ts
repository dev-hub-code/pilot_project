import { z } from "zod";

export const staffSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
  firstName: z.string().trim().min(1, "Enter a first name").max(100),
  lastName: z.string().trim().min(1, "Enter a last name").max(100),
  roles: z.array(z.string()).min(1, "Choose at least one role").max(20),
});
