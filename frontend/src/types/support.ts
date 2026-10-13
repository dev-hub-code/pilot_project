/** Mirrors of the backend's Phase 10 DTOs (support tickets and notifications). */

export type TicketStatus = "OPEN" | "WAITING_ON_CUSTOMER" | "RESOLVED" | "CLOSED";
export type TicketPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type TicketCategory = "ACCOUNT" | "INVESTMENT" | "PAYMENT" | "EARNINGS" | "WITHDRAWAL" | "REFERRAL" | "OTHER";
export type RelatedType = "ORDER" | "WITHDRAWAL" | "HOLDING";

/** Requester/assignee fields and response targets are only present in staff views. */
export interface Ticket {
  id: string;
  reference: string;
  subject: string;
  category: TicketCategory;
  relatedType: RelatedType | null;
  relatedId: string | null;
  relatedLabel: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  requesterId: string | null;
  requesterName: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  firstResponseDueAt: string | null;
  firstRespondedAt: string | null;
  overdue: boolean;
  lastMessageAt: string;
  createdAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  /** Set only while a resolved or closed ticket can still be reopened by its customer. */
  reopenableUntil: string | null;
}

export interface TicketAttachment {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
}

export interface TicketMessage {
  id: string;
  fromStaff: boolean;
  internal: boolean;
  authorName: string | null;
  body: string;
  createdAt: string;
  attachments: TicketAttachment[];
}

export type TicketEventType = "OPENED" | "STATUS_CHANGED" | "REOPENED" | "ASSIGNED" | "UNASSIGNED" | "PRIORITY_CHANGED";

/** One update in a ticket's history; customers never receive priority changes or unassignments. */
export interface TicketEvent {
  id: string;
  type: TicketEventType;
  /** Previous and new status or priority, when the event changed one. */
  fromValue: string | null;
  toValue: string | null;
  byStaff: boolean;
  actorName: string | null;
  /** For assignments: the agent now handling the ticket. */
  agentName: string | null;
  createdAt: string;
}

export interface TicketDetail {
  ticket: Ticket;
  messages: TicketMessage[];
  events: TicketEvent[];
}

export interface Agent {
  userId: string;
  name: string;
  email: string;
}

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
}
