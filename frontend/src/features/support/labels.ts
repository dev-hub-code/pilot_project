import type { StatusTone } from "@/components/ui/status-badge";
import type { TicketCategory, TicketEvent, TicketPriority, TicketStatus } from "@/types/support";

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
  EARNINGS: "Payouts & earnings",
  WITHDRAWAL: "A withdrawal",
  REFERRAL: "Referrals",
  OTHER: "Something else",
};

const PRIORITY_LABEL: Record<TicketPriority, string> = { LOW: "Low", NORMAL: "Normal", HIGH: "High", URGENT: "Urgent" };

/** One line describing a ticket update, worded for the customer or for staff. */
export function describeEvent(e: TicketEvent, viewer: "customer" | "staff"): string {
  const labels = viewer === "customer" ? TICKET_STATUS_LABEL : STAFF_STATUS_LABEL;
  const status = (value: string | null) => (value ? labels[value as TicketStatus] ?? value : "");
  const actor = e.actorName ?? (e.byStaff ? "SeaLease support" : "The customer");
  switch (e.type) {
    case "OPENED":
      return viewer === "customer" ? "You opened this request" : `${actor} opened the ticket`;
    case "REOPENED":
      return `${actor} reopened the request${e.fromValue ? ` (it was ${status(e.fromValue).toLowerCase()})` : ""}`;
    case "ASSIGNED":
      return viewer === "customer"
        ? `${e.agentName ?? "A support agent"} is now handling your request`
        : `${actor} assigned the ticket to ${e.agentName ?? "an agent"}`;
    case "UNASSIGNED":
      return `${actor} unassigned the ticket`;
    case "TEAM_CHANGED":
      return `${actor} handed the ticket to ${e.toValue === "FINANCE" ? "the finance team" : "the support team"}`;
    case "PRIORITY_CHANGED":
      return `${actor} changed priority from ${PRIORITY_LABEL[e.fromValue as TicketPriority] ?? e.fromValue} to ${PRIORITY_LABEL[e.toValue as TicketPriority] ?? e.toValue}`;
    case "STATUS_CHANGED":
      if (e.toValue === "WAITING_ON_CUSTOMER") {
        return viewer === "customer" ? `${actor} replied. Waiting for your reply` : `${actor} replied. Waiting on the customer`;
      }
      if (e.toValue === "RESOLVED") return `${actor} marked the request as resolved`;
      if (e.toValue === "CLOSED") {
        // No actor and from resolved: closed by itself after 24 hours.
        return e.actorName === null && e.byStaff && e.fromValue === "RESOLVED"
          ? "The request closed automatically, 24 hours after it was resolved"
          : `${actor} closed the request`;
      }
      if (e.toValue === "OPEN" && e.fromValue === "WAITING_ON_CUSTOMER") {
        return viewer === "customer" ? "You replied. Back with our support team" : `${actor} replied. Back in the queue`;
      }
      return `${actor} changed the status${e.fromValue ? ` from ${status(e.fromValue)}` : ""} to ${status(e.toValue)}`;
  }
}

export function formatBytes(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
