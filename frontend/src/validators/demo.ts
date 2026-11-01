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
  amount: z.string().trim().regex(/^\d{1,10}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals"),
  startsOn: day,
});

export const demoMemberSchema = z.object({
  parentId: optionalId,
  name: z.string().trim().min(1, "Enter a name").max(120),
  joinedOn: day,
});
