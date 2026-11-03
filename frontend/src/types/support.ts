/** Mirrors of the backend's Phase 10 DTOs (support tickets and notifications). */

export type TicketStatus = "NEW" | "OPEN" | "IN_PROGRESS" | "WAITING_ON_CUSTOMER" | "WAITING_ON_INTERNAL" | "ESCALATED"
  | "RESOLVED" | "CLOSED" | "REOPENED";
/** Stored names; shown as Low, Medium, High and Critical. */
export type TicketPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type TicketCategory = "ACCOUNT" | "INVESTMENT" | "PAYMENT" | "EARNINGS" | "WITHDRAWAL" | "REFERRAL" | "KYC"
  | "CONTAINER" | "TECHNICAL" | "OTHER";
export type TicketChannel = "PORTAL" | "STAFF" | "SYSTEM" | "EMAIL";
export type SlaState = "BREACHED" | "AT_RISK" | "PAUSED" | "ON_TRACK" | "MET" | "NONE";
export type RelatedType = "ORDER" | "WITHDRAWAL" | "HOLDING";

/** Requester/assignee fields and response targets are only present in staff views. */
export interface Ticket {
  id: string;
  reference: string;
  subject: string;
  category: TicketCategory;
  subcategoryId: string | null;
  subcategoryName: string | null;
  relatedType: RelatedType | null;
  relatedId: string | null;
  relatedLabel: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  requesterId: string | null;
  requesterName: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  /** Staff views only. */
  team: SupportTeam | null;
  channel: TicketChannel | null;
  firstResponseDueAt: string | null;
  firstRespondedAt: string | null;
  nextResponseDueAt: string | null;
  resolutionDueAt: string | null;
  slaState: SlaState | null;
  escalationLevel: number | null;
  reopenCount: number | null;
  overdue: boolean;
  lastMessageAt: string;
  createdAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  /** Set only while a resolved or closed ticket can still be reopened by its customer. */
  reopenableUntil: string | null;
  /** Resolved: the customer is invited to rate the help they got. */
  feedbackRequested: boolean;
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

export type TicketEventType = "OPENED" | "STATUS_CHANGED" | "REOPENED" | "ASSIGNED" | "UNASSIGNED" | "PRIORITY_CHANGED"
  | "TEAM_CHANGED" | "CATEGORY_CHANGED" | "ESCALATED" | "SLA_WARNING" | "SLA_BREACHED" | "SLA_PAUSED" | "SLA_RESUMED"
  | "FEEDBACK";

/** The team a ticket is with: support hands tickets that need finance to the finance team. */
export type SupportTeam = "SUPPORT" | "FINANCE";

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

export interface SupportCategory {
  id: string;
  parentId: string | null;
  category: TicketCategory;
  code: string;
  name: string;
  description: string | null;
  defaultPriority: TicketPriority | null;
  defaultTeam: SupportTeam | null;
  active: boolean;
  sortOrder: number;
}

export interface SlaTarget {
  metric: "FIRST_RESPONSE" | "NEXT_RESPONSE" | "RESOLUTION";
  dueAt: string | null;
  metAt: string | null;
  state: SlaState;
  percentUsed: number | null;
  minutesLeft: number | null;
}

export interface SlaView {
  policyId: string | null;
  policyName: string | null;
  businessHours: boolean;
  paused: boolean;
  firstResponse: SlaTarget;
  nextResponse: SlaTarget;
  resolution: SlaTarget;
  overall: SlaState;
}

export type EscalationTrigger = "MANUAL" | "FIRST_RESPONSE_AT_RISK" | "FIRST_RESPONSE_BREACHED" | "RESOLUTION_BREACHED"
  | "CRITICAL_UNACKNOWLEDGED" | "REPEATED_FOLLOW_UPS" | "REPEATED_REOPENS" | "AUTOMATION";

export interface Escalation {
  id: string;
  ticketId: string;
  reference: string;
  subject: string;
  level: number;
  previousLevel: number;
  trigger: EscalationTrigger;
  reason: string;
  escalatedBy: string | null;
  escalatedByName: string | null;
  ownerId: string | null;
  ownerName: string | null;
  createdAt: string;
  status: TicketStatus;
}

export interface EscalationLevel {
  level: number;
  name: string;
  description: string | null;
  contacts: { userId: string; name: string; email: string }[];
}

export interface TicketFeedback {
  ticketId: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

/** The workspace's side panel. Customer fields the viewer may not see are null. */
export interface TicketContext {
  sla: SlaView;
  escalations: Escalation[];
  feedback: TicketFeedback | null;
  customer: {
    id: string;
    name: string;
    userCode: string | null;
    email: string | null;
    status: string;
    customerType: "NETWORK" | "ONLINE";
    kycStatus: string | null;
    registeredAt: string;
    openTickets: number;
    totalTickets: number;
  } | null;
  previousTickets: { id: string; reference: string; subject: string; status: TicketStatus; createdAt: string }[];
  investments: { activeHoldings: number; investedAmount: number; orders: number; lastOrderNumber: string | null; lastOrderStatus: string | null } | null;
  money: { pendingWithdrawals: number; lastWithdrawalReference: string | null; lastWithdrawalStatus: string | null;
    lastWithdrawalAmount: number | null; paymentsAwaiting: number } | null;
}

export interface Workload {
  agentId: string;
  name: string;
  teams: SupportTeam[];
  open: number;
  inProgress: number;
  waiting: number;
  breached: number;
  resolvedRecently: number;
}

export interface SupportKpi {
  key: string;
  label: string;
  value: number | null;
  previous: number | null;
  unit: "count" | "minutes" | "percent" | "rating";
  higherBetter: boolean;
  drill: string | null;
}

export interface SupportDashboard {
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  timeZone: string;
  kpis: SupportKpi[];
  trend: { day: string; created: number; resolved: number; breaches: number }[];
  byStatus: Slice[];
  byPriority: Slice[];
  byCategory: Slice[];
  byChannel: Slice[];
  aging: Slice[];
  workload: Workload[];
  recentEscalations: Escalation[];
  csat: { weekStart: string; average: number | null; ratings: number }[];
  ratings: Slice[];
}

export interface Slice {
  key: string;
  count: number;
}

export interface FeedbackRow {
  ticketId: string;
  reference: string;
  subject: string;
  rating: number;
  comment: string | null;
  createdAt: string;
  customerId: string;
  customerName: string;
  assigneeId: string | null;
  assigneeName: string | null;
  category: TicketCategory;
}

export interface CustomerOption {
  id: string;
  name: string;
  userCode: string | null;
  email: string | null;
}

export interface BulkAssignResult {
  ticketId: string;
  reference: string | null;
  assigned: boolean;
  assigneeId: string | null;
  error: string | null;
}

// ------------------------------------------------------------------------------------------- configuration

export interface SlaPolicy {
  id: string;
  name: string;
  priority: TicketPriority | null;
  category: TicketCategory | null;
  customerType: "NETWORK" | "ONLINE" | null;
  firstResponseMinutes: number;
  nextResponseMinutes: number | null;
  resolutionMinutes: number;
  businessHours: boolean;
  pauseWhileWaiting: boolean;
  active: boolean;
  updatedAt: string;
}

export interface SupportCalendar {
  timeZone: string;
  hours: { dayOfWeek: number; opensAt: string; closesAt: string }[];
  holidays: { day: string; name: string }[];
}

export type AssignmentStrategy = "MANUAL" | "ROUND_ROBIN" | "LEAST_LOADED";

export interface AssignmentRule {
  category: TicketCategory;
  strategy: AssignmentStrategy;
  members: { userId: string; name: string }[];
  updatedAt: string;
}

export type AutomationTrigger = "TICKET_CREATED" | "UNASSIGNED_FOR" | "SLA_PERCENT" | "SLA_BREACHED"
  | "CUSTOMER_REPLIED_RESOLVED" | "TICKET_RESOLVED" | "TICKET_CLOSED" | "TICKET_REOPENED";
export type AutomationAction = "NOTIFY_ASSIGNEE" | "NOTIFY_TEAM" | "NOTIFY_ESCALATION_CONTACTS" | "AUTO_ASSIGN" | "ESCALATE"
  | "SET_PRIORITY" | "REQUEST_FEEDBACK" | "RECORD_RESOLUTION_METRICS";

export interface AutomationRule {
  id: string;
  name: string;
  trigger: AutomationTrigger;
  conditions: Record<string, unknown>;
  action: AutomationAction;
  parameters: Record<string, unknown>;
  enabled: boolean;
  updatedAt: string;
  runs: number;
  failures: number;
  lastRunAt: string | null;
}

export interface AutomationRun {
  id: string;
  ruleId: string;
  ruleName: string;
  ticketId: string;
  reference: string;
  status: "SUCCEEDED" | "FAILED" | "SKIPPED";
  attempts: number;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

// ------------------------------------------------------------------------------------------- knowledge base

export type ArticleVisibility = "PUBLIC" | "CUSTOMER" | "INTERNAL";
export type ArticleStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface KbCategory {
  id: string;
  code: string;
  name: string;
  description: string | null;
  sortOrder: number;
  articles: number;
}

export interface ArticleSummary {
  id: string;
  slug: string;
  title: string;
  summary: string;
  categoryCode: string;
  categoryName: string;
  visibility: ArticleVisibility;
  status: ArticleStatus;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
  views: number;
  helpful: number;
  notHelpful: number;
}

export interface Article {
  summary: ArticleSummary;
  body: string;
  currentVersion: number;
  authorName: string;
  updatedByName: string;
  related: ArticleSummary[];
}

export interface ArticleVersion {
  version: number;
  title: string;
  summary: string;
  body: string;
  editedByName: string;
  createdAt: string;
}

export interface KbPage<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

export interface ArticleFeedbackStats {
  articleId: string;
  title: string;
  helpful: number;
  notHelpful: number;
  comments: { helpful: boolean; comment: string; createdAt: string }[];
}
