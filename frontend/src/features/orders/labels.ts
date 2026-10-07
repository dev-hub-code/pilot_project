import type { StatusTone } from "@/components/ui/status-badge";
import type { OrderStatus, PaymentMethod, PaymentStatus } from "@/types/order";

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

export const METHOD_LABEL: Record<PaymentMethod, string> = { BANK_TRANSFER: "Bank transfer", CARD: "Card" };
