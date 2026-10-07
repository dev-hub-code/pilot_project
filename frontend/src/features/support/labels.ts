import type { StatusTone } from "@/components/ui/status-badge";
import type { TicketCategory, TicketPriority, TicketStatus } from "@/types/support";

export const TICKET_STATUS_LABEL: Record<TicketStatus, string> = {
  OPEN: "Open",
  WAITING_ON_CUSTOMER: "Awaiting your reply",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};

/** Staff read WAITING_ON_CUSTOMER from the other side. */
export const STAFF_STATUS_LABEL: Record<TicketStatus, string> = { ...TICKET_STATUS_LABEL, WAITING_ON_CUSTOMER: "Waiting on customer" };

export const TICKET_TONE: Record<TicketStatus, StatusTone> = {
  OPEN: "warning",
  WAITING_ON_CUSTOMER: "neutral",
  RESOLVED: "success",
  CLOSED: "neutral",
};

export const PRIORITY_TONE: Record<TicketPriority, StatusTone> = {
  LOW: "neutral",
  NORMAL: "neutral",
  HIGH: "warning",
  URGENT: "danger",
};

export const CATEGORY_LABEL: Record<TicketCategory, string> = {
  ACCOUNT: "My account",
  INVESTMENT: "An investment",
  PAYMENT: "A payment",
  EARNINGS: "Rental income",
  WITHDRAWAL: "A withdrawal",
  REFERRAL: "Referrals",
  OTHER: "Something else",
};

export function formatBytes(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
