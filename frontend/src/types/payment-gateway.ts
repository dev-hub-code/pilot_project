import type { Money } from "./plan";

/** How online payments through Razorpay run (Platform config → Payment gateway). The secrets are never sent here. */
export interface PaymentGatewaySettings {
  /** The Razorpay API keys are set on the server; without them nothing here takes effect. */
  keysConfigured: boolean;
  mode: "test" | "live" | null;
  webhookConfigured: boolean;
  onlinePayments: boolean;
  /** The most one checkout payment can be; zero for no limit. */
  maxAmount: Money;
  checkoutWindowMinutes: number;
  /** The server's configuration, which applies until an admin saves. */
  defaults: {
    onlinePayments: boolean;
    maxAmount: Money;
    checkoutWindowMinutes: number;
  };
  updatedAt: string | null;
  updatedByName: string | null;
  keys: PaymentGatewayKeys;
}

/** The Razorpay credentials in force, without the secrets (only their last four characters). */
export interface PaymentGatewayKeys {
  keyId: string | null;
  keySecretHint: string | null;
  webhookSecretHint: string | null;
  merchantName: string;
  /** ADMIN: saved in the admin panel; SERVER: the server's environment; null: not set. */
  source: "ADMIN" | "SERVER" | null;
  webhookSource: "ADMIN" | "SERVER" | null;
  updatedAt: string | null;
  updatedByName: string | null;
}
