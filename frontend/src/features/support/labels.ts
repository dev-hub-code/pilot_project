import type { StatusTone } from "@/components/ui/status-badge";
import type { EscalationTrigger, SlaState, TicketCategory, TicketChannel, TicketEvent, TicketPriority, TicketStatus } from "@/types/support";

/** As customers read them: internal stages are simply "being worked on". */
export const TICKET_STATUS_LABEL: Record<TicketStatus, string> = {
  NEW: "Received",
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  WAITING_ON_CUSTOMER: "Awaiting your reply",
  WAITING_ON_INTERNAL: "In progress",
  ESCALATED: "In progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
  REOPENED: "Reopened",
};

/** Staff see every stage. */
export const STAFF_STATUS_LABEL: Record<TicketStatus, string> = {
  ...TICKET_STATUS_LABEL,
  NEW: "New",
  WAITING_ON_CUSTOMER: "Waiting on customer",
  WAITING_ON_INTERNAL: "Waiting on internal team",
  ESCALATED: "Escalated",
};

export const TICKET_TONE: Record<TicketStatus, StatusTone> = {
  NEW: "warning",
  OPEN: "warning",
  IN_PROGRESS: "warning",
  WAITING_ON_CUSTOMER: "neutral",
  WAITING_ON_INTERNAL: "neutral",
  ESCALATED: "danger",
  RESOLVED: "success",
  CLOSED: "neutral",
  REOPENED: "warning",
};

/** Customers see internal stages as in progress. */
export const CUSTOMER_TONE: Record<TicketStatus, StatusTone> = {
  ...TICKET_TONE, NEW: "neutral", IN_PROGRESS: "warning", WAITING_ON_INTERNAL: "warning", ESCALATED: "warning",
};

/** Statuses staff set by hand; the others follow from replies, escalation and reopening. */
export const STAFF_SETTABLE: readonly TicketStatus[] = ["IN_PROGRESS", "WAITING_ON_CUSTOMER", "WAITING_ON_INTERNAL", "RESOLVED", "CLOSED"];

const TRANSITIONS: Record<TicketStatus, readonly TicketStatus[]> = {
  NEW: STAFF_SETTABLE,
  OPEN: STAFF_SETTABLE,
  IN_PROGRESS: ["WAITING_ON_CUSTOMER", "WAITING_ON_INTERNAL", "RESOLVED", "CLOSED"],
  WAITING_ON_CUSTOMER: ["IN_PROGRESS", "WAITING_ON_INTERNAL", "RESOLVED", "CLOSED"],
  WAITING_ON_INTERNAL: ["IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED"],
  ESCALATED: STAFF_SETTABLE,
  REOPENED: STAFF_SETTABLE,
  RESOLVED: ["CLOSED"],
  CLOSED: [],
};

/** Where staff may move a ticket from its status (the API enforces the same rules). */
export function nextStatuses(status: TicketStatus): readonly TicketStatus[] {
  return TRANSITIONS[status];
}

export const SLA_LABEL: Record<SlaState, string> = {
  BREACHED: "Breached",
  AT_RISK: "At risk",
  PAUSED: "Paused",
  ON_TRACK: "On track",
  MET: "Met",
  NONE: "No target",
};

export const SLA_TONE: Record<SlaState, StatusTone> = {
  BREACHED: "danger",
  AT_RISK: "warning",
  PAUSED: "neutral",
  ON_TRACK: "success",
  MET: "success",
  NONE: "neutral",
};

export const CHANNEL_LABEL: Record<TicketChannel, string> = {
  PORTAL: "Customer portal",
  STAFF: "Opened by staff",
  SYSTEM: "Opened by the system",
  EMAIL: "Email",
};

export const TRIGGER_LABEL: Record<EscalationTrigger, string> = {
  MANUAL: "Escalated by an agent",
  FIRST_RESPONSE_AT_RISK: "First response at risk",
  FIRST_RESPONSE_BREACHED: "First response breached",
  RESOLUTION_BREACHED: "Resolution breached",
  CRITICAL_UNACKNOWLEDGED: "Critical, not acknowledged",
  REPEATED_FOLLOW_UPS: "Repeated follow-ups",
  REPEATED_REOPENS: "Repeated reopens",
  AUTOMATION: "Automation rule",
};

export const STATUS_ORDER: readonly TicketStatus[] = ["NEW", "OPEN", "REOPENED", "IN_PROGRESS", "ESCALATED",
  "WAITING_ON_INTERNAL", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED"];

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
  KYC: "KYC & verification",
  TECHNICAL: "Technical support",
  OTHER: "Something else",
};

export const CATEGORY_ORDER: readonly TicketCategory[] = ["ACCOUNT", "INVESTMENT", "PAYMENT", "EARNINGS", "WITHDRAWAL", "KYC",
  "REFERRAL", "TECHNICAL", "OTHER"];

/** Stored NORMAL and URGENT read as Medium and Critical. */
export const PRIORITY_LABEL: Record<TicketPriority, string> = { LOW: "Low", NORMAL: "Medium", HIGH: "High", URGENT: "Critical" };

export const PRIORITY_ORDER: readonly TicketPriority[] = ["URGENT", "HIGH", "NORMAL", "LOW"];

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
    case "CATEGORY_CHANGED":
      return `${actor} moved the ticket to ${CATEGORY_LABEL[e.toValue as TicketCategory] ?? e.toValue}`;
    case "ESCALATED":
      return `${e.actorName ?? "Automatic escalation"}: level ${e.fromValue?.replace("L", "")} to ${e.toValue?.replace("L", "")}`
        + (e.agentName ? `, owned by ${e.agentName}` : "");
    case "SLA_WARNING":
      return `SLA warning: ${e.fromValue} of the ${(e.toValue ?? "").replace("_", " ").toLowerCase()} target used`;
    case "SLA_BREACHED":
      return `SLA breached: ${(e.toValue ?? "").replace("_", " ").toLowerCase()} target missed`;
    case "SLA_PAUSED":
      return "Resolution clock paused while waiting on the customer";
    case "SLA_RESUMED":
      return "Resolution clock restarted";
    case "FEEDBACK":
      return viewer === "customer" ? `You rated this request ${e.toValue}/5` : `The customer rated this ticket ${e.toValue}/5`;
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
      if (e.toValue === "ESCALATED") return viewer === "customer" ? "Your request was passed to a senior team member" : `${actor} escalated the ticket`;
      if (e.toValue === "OPEN" && e.fromValue === "WAITING_ON_CUSTOMER") {
        return viewer === "customer" ? "You replied. Back with our support team" : `${actor} replied. Back in the queue`;
      }
      return `${actor} changed the status${e.fromValue ? ` from ${status(e.fromValue)}` : ""} to ${status(e.toValue)}`;
  }
}

export function formatBytes(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
