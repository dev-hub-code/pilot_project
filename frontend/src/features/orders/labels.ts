import type { StatusTone } from "@/components/ui/status-badge";
import type { DepositMode, OrderStatus, PaymentMethod, PaymentStatus } from "@/types/order";

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Awaiting payment",
  CONFIRMED: "Confirmed",
  EXPIRED: "Expired",
  CANCELLED: "Cancelled",
};

export const ORDER_TONE: Record<OrderStatus, StatusTone> = {
  PENDING_PAYMENT: "warning",
  CONFIRMED: "success",
  EXPIRED: "neutral",
  CANCELLED: "neutral",
};

export const PAYMENT_TONE: Record<PaymentStatus, StatusTone> = {
  PENDING: "warning",
  SUCCEEDED: "success",
  FAILED: "danger",
  CANCELLED: "neutral",
  REFUND_REQUIRED: "danger",
  REFUNDED: "neutral",
};

export const METHOD_LABEL: Record<PaymentMethod, string> = { BANK_TRANSFER: "Bank payment", CARD: "Card" };

export const DEPOSIT_MODE_LABEL: Record<DepositMode, string> = {
  ONLINE: "Online transfer",
  CHEQUE: "Cheque",
  CASH_DEPOSIT: "Cash deposit",
};

/** What the investor's reference is called for each way of paying. */
export const DEPOSIT_REFERENCE_LABEL: Record<DepositMode, string> = {
  ONLINE: "Transaction ID (UTR)",
  CHEQUE: "Cheque number",
  CASH_DEPOSIT: "Deposit receipt number",
};

export const DEPOSIT_MODE_HINT: Record<DepositMode, string> = {
  ONLINE: "NEFT, RTGS, IMPS or UPI. The transaction ID is on your bank's confirmation.",
  CHEQUE: "Write the payment reference on the back of the cheque. Cheques take a few days to clear.",
  CASH_DEPOSIT: "Quote the payment reference on the deposit slip. The receipt number is on the counterfoil.",
};

/** "2 × PLAN-10001, 1 × PLAN-10002": an order's containers, per plan. */
export function containersSummary(items: readonly { productCode: string }[]): string {
  const counts = new Map<string, number>();
  for (const item of items) counts.set(item.productCode, (counts.get(item.productCode) ?? 0) + 1);
  return [...counts].map(([code, n]) => `${n} × ${code}`).join(", ");
}
