import type { TicketPriority } from "@/types/support";
/** Mirrors of the backend's report documents (Phase 11). */

import type { Money } from "@/types/marketplace";

export interface ReportRow {
  c1: string;
  c2: string;
  c3: string;
  c4: string;
  emphasis: boolean;
}

export interface ReportSection {
  name: string;
  headers: [string, string, string, string];
  rows: ReportRow[];
}

export interface ReportDocument {
  title: string;
  subtitle: string;
  filename: string;
  generatedAt: string;
  sections: ReportSection[];
  footnote: string | null;
}

/** One queue: pending, the viewer's, and done today and this month (by anyone, and by the viewer). */
export interface WorkloadCounts {
  pending: number;
  /** Pending tickets past their first-response target; null for finance tasks. */
  overdue: number | null;
  assignedToMe: number;
  doneToday: number;
  doneTodayByMe: number;
  doneThisMonth: number;
  doneThisMonthByMe: number;
}

/** Pending support tickets of one priority. */
export interface PriorityRow {
  priority: TicketPriority;
  pending: number;
  overdue: number;
  /** Waiting for a reply with nobody on them. */
  unassigned: number;
  assignedToMe: number;
}

/** The queues the viewer works; a queue they do not work on is null. */
export interface Workload {
  financeTasks: WorkloadCounts | null;
  financeTickets: WorkloadCounts | null;
  supportTickets: WorkloadCounts | null;
  /** Most urgent first. */
  supportByPriority: PriorityRow[] | null;
}

export interface KpiTile {
  key: string;
  label: string;
  values: string[];
  note: string;
  link: string;
}

/** Twelve months of trends for the admin overview; a series the viewer may not see is null. */
export interface DashboardCharts {
  payouts: { month: string; rent: Money; capital: Money; referral: Money }[] | null;
  invested: { month: string; amount: Money }[] | null;
  investors: { month: string; count: number }[] | null;
  pipeline: { stage: string; leads: number }[] | null;
}
