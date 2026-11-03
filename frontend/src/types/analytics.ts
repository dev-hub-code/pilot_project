/** The reporting API (/api/v1/admin/analytics). Amounts are decimal strings, never floats. */
export type ColumnType = "TEXT" | "STATUS" | "MONEY" | "NUMBER" | "PERCENT" | "DATE" | "MONTH" | "DATETIME";

export interface ReportColumn {
  key: string;
  label: string;
  type: ColumnType;
  sortable: boolean;
  /** "user" or "order": the cell links to that record. */
  link: string | null;
  masked: boolean;
}

export interface CatalogEntry {
  key: string;
  title: string;
  description: string;
  usesPeriod: boolean;
  dateLabel: string | null;
  choices: { key: string; label: string; values: string[] }[];
  searchable: boolean;
  amountFilter: boolean;
  columns: ReportColumn[];
  defaultSort: string;
  defaultDescending: boolean;
}

export interface CatalogCategory {
  key: string;
  label: string;
  reports: CatalogEntry[];
}

export interface ReportResult {
  key: string;
  title: string;
  columns: ReportColumn[];
  rows: (string | number | null)[][];
  links: Record<string, string>[];
  totalRows: number;
  page: number;
  size: number;
  totals: { currency: string; values: Record<string, string | number> }[];
  generatedAt: string;
  note: string | null;
}

export interface Kpi {
  key: string;
  label: string;
  kind: "MONEY" | "COUNT" | "PERCENT";
  value: string | number;
  previous: string | number | null;
  change: string | number | null;
  report: string;
  hint: string | null;
}

export interface Overview {
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  currency: string;
  period: Kpi[];
  now: Kpi[];
  trends: { month: string; values: Record<string, string | number> }[];
  containers: { key: string; count: number }[];
  generatedAt: string;
}

export interface Execution {
  id: string;
  reportKey: string;
  reportTitle: string;
  executedBy: string;
  executedAt: string;
  format: "CSV" | "XLSX" | "PDF";
  filters: string;
  rows: number | null;
  durationMs: number | null;
  status: "COMPLETED" | "FAILED";
  failure: string | null;
}

export interface HistoryPage {
  executions: Execution[];
  total: number;
  page: number;
  size: number;
  everyone: boolean;
}
