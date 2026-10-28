import { z } from "zod";

const day = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Choose a date");
const uuid = z.string().uuid();
/** An empty select means "none". */
const optionalId = z.string().transform((v) => (v === "" ? null : v)).pipe(uuid.nullable());

export const demoFrameSchema = z.object({ startsOn: day, endsOn: day })
  .refine((f) => f.endsOn >= f.startsOn, { path: ["endsOn"], message: "Must be on or after the start" });

export const demoAccountSchema = z.object({ userId: uuid }).and(demoFrameSchema);

export const demoOrderSchema = z.object({
  memberId: optionalId,
  productId: z.string().uuid("Choose a plan"),
  containers: z.coerce.number({ message: "Enter a number" }).int("Whole containers only").min(1, "At least 1").max(1000),
  tenureYears: z.coerce.number({ message: "Choose the tenure" }).int().min(1, "Choose the tenure").max(50),
  startsOn: day,
});

export const demoMemberSchema = z.object({
  parentId: optionalId,
  name: z.string().trim().min(1, "Enter a name").max(120),
  joinedOn: day,
});
