"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import type { CheckoutInstructions } from "@/types/order";
import { formatMoney } from "@/utils/money";
import type { FormState } from "@/validators/form-state";
import { verifyCheckoutAction } from "./actions";

const SCRIPT = "https://checkout.razorpay.com/v1/checkout.js";

interface RazorpayResult {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayInstance {
  open(): void;
  on(event: "payment.failed", handler: (response: { error?: { description?: string } }) => void): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

/** Loads Razorpay's checkout script once (the CSP's strict-dynamic trusts scripts our own code adds). */
function loadCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT}"]`);
    const script = existing ?? Object.assign(document.createElement("script"), { src: SCRIPT, async: true });
    script.addEventListener("load", () => resolve(), { once: true });
    script.addEventListener("error", () => reject(new Error("checkout script")), { once: true });
    if (!existing) document.body.appendChild(script);
  });
}

/**
 * Opens the gateway's checkout for a pending online payment (UPI, net banking or card). Its signed
 * result goes to the API, which checks it with the gateway before confirming the order. If the browser closes before
 * then, the gateway's webhook confirms it anyway.
 */
export function RazorpayCheckout({ orderId, paymentId, checkout }: {
  orderId: string;
  paymentId: string;
  checkout: CheckoutInstructions;
}) {
  const [state, setState] = useState<FormState>({});
  const [busy, setBusy] = useState(false);
  const amount = formatMoney({ amount: (checkout.amountPaise / 100).toFixed(2), currency: checkout.currency });

  async function pay() {
    setBusy(true);
    setState({});
    try {
      await loadCheckout();
    } catch {
      setState({ error: "The payment window could not be loaded. Check your connection and try again." });
      setBusy(false);
      return;
    }
    if (!window.Razorpay) {
      setState({ error: "The payment window could not be loaded. Try again." });
      setBusy(false);
      return;
    }
    const razorpay = new window.Razorpay({
      key: checkout.keyId,
      order_id: checkout.orderId,
      amount: checkout.amountPaise,
      currency: checkout.currency,
      name: checkout.merchantName,
      description: checkout.description,
      prefill: { name: checkout.prefillName ?? undefined, email: checkout.prefillEmail ?? undefined },
      method: { upi: true, netbanking: true, card: true, wallet: false, emi: false, paylater: false },
      modal: { ondismiss: () => setBusy(false) },
      handler: async (result: RazorpayResult) => {
        const outcome = await verifyCheckoutAction(orderId, paymentId, {
          gatewayOrderId: result.razorpay_order_id,
          gatewayPaymentId: result.razorpay_payment_id,
          signature: result.razorpay_signature,
        });
        setState(outcome.error
          ? { error: `${outcome.error} If money left your account, your order is confirmed automatically within a few minutes.` }
          : outcome);
        setBusy(false);
      },
    });
    razorpay.on("payment.failed", (response) => {
      setState({ error: response.error?.description ?? "The payment did not go through. You can try again." });
    });
    razorpay.open();
  }

  return (
    <div className="space-y-3">
      <FormFeedback state={state} />
      <Button type="button" onClick={pay} disabled={busy} className="w-full">
        {busy ? "Opening payment…" : `Pay ${amount} online`}
      </Button>
      <p className="text-xs text-muted">
        UPI, net banking or card, through Razorpay. UPI usually allows up to ₹1 lakh per payment; for more, use net banking or a
        bank transfer.
      </p>
    </div>
  );
}
