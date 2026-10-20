import type { AppNotification } from "@/types/support";

/** What opening each kind of notification lets the reader do; every notification opens its link when clicked. */
const ACTION: Record<string, string> = {
  // Its link opens the container's lease agreement (or, once accepted, the portfolio to download it).
  CONTAINER_ASSIGNED: "Accept agreement",
  LEASE_STARTED: "View portfolio",
  KYC_APPROVED: "View verification",
  KYC_REJECTED: "Fix and resubmit",
  BANK_ACCOUNT_VERIFIED: "View bank accounts",
  BANK_ACCOUNT_REJECTED: "Add the account again",
  TICKET_REPLY: "View ticket",
  TICKET_STATUS: "View ticket",
  TICKET_CUSTOMER_REPLY: "Reply to customer",
  TICKET_ASSIGNED: "Open ticket",
  TICKET_REOPENED: "Open ticket",
  TICKET_HANDED_TO_FINANCE: "Open ticket",
};

/** The call to action shown on a notification, by where it leads; null when it has no link. */
export function notificationAction(n: AppNotification): string | null {
  if (!n.link) return null;
  return ACTION[n.type] ?? "Open";
}
