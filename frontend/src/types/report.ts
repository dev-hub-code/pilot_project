/** Mirrors of the backend's report documents (Phase 11). */

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
