import type { Money } from "./plan";

/** How online payments through Razorpay run (Platform config → Payment gateway). The API keys are never sent here. */
export interface PaymentGatewaySettings {
  /** The Razorpay API keys are set on the server; without them nothing here takes effect. */
  keysConfigured: boolean;
  mode: "test" | "live" | null;
  webhookConfigured: boolean;
  onlinePayments: boolean;
  virtualAccounts: boolean;
  /** The most one checkout payment can be; zero for no limit. */
  maxAmount: Money;
  checkoutWindowMinutes: number;
  virtualAccountWindowHours: number;
  /** The server's configuration, which applies until an admin saves. */
  defaults: {
    onlinePayments: boolean;
    virtualAccounts: boolean;
    maxAmount: Money;
    checkoutWindowMinutes: number;
    virtualAccountWindowHours: number;
  };
  updatedAt: string | null;
  updatedByName: string | null;
}
