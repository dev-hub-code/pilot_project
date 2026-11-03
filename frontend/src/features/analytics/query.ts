import type { CatalogEntry } from "@/types/analytics";

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;
type Params = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function all(value: string | string[] | undefined): string[] {
  return value === undefined ? [] : Array.isArray(value) ? value : [value];
}

export interface ReportState {
  from?: string;
  to?: string;
  search?: string;
  min?: string;
  max?: string;
  sort?: string;
  desc?: boolean;
  page: number;
  size: number;
  choices: Record<string, string[]>;
  hidden: string[];
}

/** Reads a report's filters from the page URL, keeping only what the report offers (the backend checks again). */
export function readState(report: CatalogEntry, params: Params): ReportState {
  const from = first(params.from);
  const to = first(params.to);
  const sort = first(params.sort);
  const page = Number.parseInt(first(params.page) ?? "0", 10);
  const size = Number.parseInt(first(params.size) ?? "50", 10);
  const choices: Record<string, string[]> = {};
  for (const choice of report.choices) {
    const values = all(params[`f.${choice.key}`]).filter((v) => choice.values.includes(v));
    if (values.length) choices[choice.key] = values;
  }
  const amount = (v: string | undefined) => (v && AMOUNT.test(v) ? v : undefined);
  return {
    from: report.usesPeriod && from && DATE.test(from) && to && DATE.test(to) ? from : undefined,
    to: report.usesPeriod && from && DATE.test(from) && to && DATE.test(to) ? to : undefined,
    search: report.searchable ? first(params.search)?.slice(0, 100) || undefined : undefined,
    min: report.amountFilter ? amount(first(params.min)) : undefined,
    max: report.amountFilter ? amount(first(params.max)) : undefined,
    sort: sort && report.columns.some((c) => c.key === sort && c.sortable) ? sort : undefined,
    desc: first(params.desc) === undefined ? undefined : first(params.desc) === "true",
    page: Number.isFinite(page) && page >= 0 ? page : 0,
    size: [25, 50, 100, 200].includes(size) ? size : 50,
    choices,
    hidden: (first(params.hide) ?? "").split(",").filter((k) => report.columns.some((c) => c.key === k)),
  };
}

/** The state as URL parameters, for the backend (no `hide`) or for links on the page. */
export function toQuery(state: ReportState, overrides: Partial<ReportState> = {}, forPage = true): URLSearchParams {
  const s = { ...state, ...overrides };
  const query = new URLSearchParams();
  if (s.from && s.to) {
    query.set("from", s.from);
    query.set("to", s.to);
  }
  if (s.search) query.set("search", s.search);
  if (s.min) query.set("min", s.min);
  if (s.max) query.set("max", s.max);
  if (s.sort) query.set("sort", s.sort);
  if (s.desc !== undefined) query.set("desc", String(s.desc));
  for (const [key, values] of Object.entries(s.choices)) for (const v of values) query.append(`f.${key}`, v);
  if (s.page) query.set("page", String(s.page));
  if (s.size !== 50) query.set("size", String(s.size));
  if (forPage && s.hidden.length) query.set("hide", s.hidden.join(","));
  return query;
}

/** Parameters the export route passes on to the backend: only these names, so nothing else reaches it. */
export function exportParams(source: URLSearchParams): URLSearchParams | null {
  const out = new URLSearchParams();
  for (const [name, value] of source) {
    if (value.length > 100) return null;
    if (["from", "to"].includes(name)) {
      if (!DATE.test(value)) return null;
      out.append(name, value);
    } else if (["min", "max"].includes(name)) {
      if (!AMOUNT.test(value)) return null;
      out.append(name, value);
    } else if (["search", "sort", "desc"].includes(name) || /^f\.[a-zA-Z0-9]{1,40}$/.test(name)) {
      out.append(name, value);
    }
  }
  return out;
}

export const REPORT_KEY = /^[a-z0-9-]{1,64}$/;
