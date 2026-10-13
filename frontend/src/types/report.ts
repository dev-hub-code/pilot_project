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
