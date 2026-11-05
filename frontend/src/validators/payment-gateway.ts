import { z } from "zod";

/** The admin's payment gateway settings; the backend validates them again. */
export const paymentGatewaySettingsSchema = z.object({
  onlinePayments: z.boolean(),
  virtualAccounts: z.boolean(),
  maxAmount: z.string().trim().regex(/^\d{1,10}(\.\d{1,2})?$/, "Enter an amount in rupees, or 0 for no limit"),
  checkoutWindowMinutes: z.coerce.number({ message: "Enter minutes" }).int("Enter whole minutes")
    .min(5, "At least 5 minutes").max(1440, "Up to 1,440 minutes (a day)"),
  virtualAccountWindowHours: z.coerce.number({ message: "Enter hours" }).int("Enter whole hours")
    .min(1, "At least 1 hour").max(720, "Up to 720 hours (30 days)"),
});
