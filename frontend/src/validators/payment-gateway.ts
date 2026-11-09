import { z } from "zod";

/** The admin's payment gateway settings; the backend validates them again. */
export const paymentGatewaySettingsSchema = z.object({
  onlinePayments: z.boolean(),
  maxAmount: z.string().trim().regex(/^\d{1,10}(\.\d{1,2})?$/, "Enter an amount in rupees, or 0 for no limit"),
  checkoutWindowMinutes: z.coerce.number({ message: "Enter minutes" }).int("Enter whole minutes")
    .min(5, "At least 5 minutes").max(1440, "Up to 1,440 minutes (a day)"),
});

/** The Razorpay credentials an admin enters; a secret left blank keeps the one saved. */
export const paymentGatewayKeysSchema = z.object({
  keyId: z.string().trim().regex(/^rzp_(test|live)_[A-Za-z0-9]{6,40}$/, "Enter the key id from Razorpay (rzp_test_… or rzp_live_…)"),
  keySecret: z.string().trim().regex(/^(\S{8,100})?$/, "Enter the key secret as Razorpay shows it"),
  webhookSecret: z.string().trim().regex(/^(\S{8,200})?$/, "At least 8 characters, without spaces"),
  merchantName: z.string().trim().max(60, "Up to 60 characters"),
});
