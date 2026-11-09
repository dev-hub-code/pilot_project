import { Building2, CalendarClock, CreditCard, FileSignature, FileText, Handshake, type LucideIcon, Wallet } from "lucide-react";

/** What each Platform config tab is for, for its icon and the overview. */
export const CONFIG_SECTIONS: Record<string, { icon: LucideIcon; blurb: string }> = {
  "/platform/platform-config/withdrawals": { icon: Wallet, blurb: "Generate returns and referral payouts, set the minimum, charges and tax, and see past runs." },
  "/platform/platform-config/returns": { icon: CalendarClock, blurb: "How often returns reach investors' wallets." },
  "/platform/platform-config/referral-rates": { icon: Handshake, blurb: "Commission per level and the online one-time rate, and when changes take effect." },
  "/platform/platform-config/payment-gateway": { icon: CreditCard, blurb: "The Razorpay connection, which ways online investors pay, the checkout limit and holds." },
  "/platform/platform-config/company-accounts": { icon: Building2, blurb: "The bank accounts investors pay into, and which ones are offered." },
  "/platform/platform-config/terms": { icon: FileText, blurb: "The terms everyone accepts when they create an account. Saving makes a new version." },
  "/platform/platform-config/agreement": { icon: FileSignature, blurb: "The agreement each investor receives when their payment is confirmed." },
};
