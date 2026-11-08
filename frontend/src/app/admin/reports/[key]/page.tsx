import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
import { findReport } from "@/features/analytics/catalog";
import { formatCell, numeric } from "@/features/analytics/format";
import { REPORT_KEY, type ReportState, readState, toQuery } from "@/features/analytics/query";
import { ReportsShell } from "@/features/analytics/reports-shell";
import { periodPresets } from "@/features/reports/report-preview";
import { CURRENCY } from "@/lib/currency";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { CatalogEntry, ReportColumn, ReportResult } from "@/types/analytics";
import { formatDate, formatDateTime, humanize } from "@/utils/format";
import { DownloadLink, PagerLinks, pill } from "@/features/analytics/report-ui";
import { Check, ChevronDown, Columns3, Filter, FolderOpen, RefreshCw, Rows3, X } from "lucide-react";

export async function generateMetadata({ params }: PageProps<"/admin/reports/[key]">): Promise<Metadata> {
  const { key } = await params;
  const found = REPORT_KEY.test(key) ? await findReport(key).catch(() => null) : null;
  return { title: found?.report.title ?? "Report" };
}

const LINKS: Record<string, string> = { user: "/admin/users/", order: "/admin/orders/" };
const chip = "inline-flex items-center gap-1.5 rounded-full bg-gold/10 py-1 pr-1 pl-3 text-xs font-medium text-gold-text ring-1 ring-gold/25";

export default async function ReportPage({ params, searchParams }: PageProps<"/admin/reports/[key]">) {
  const denied = await deniedUnlessInMenu("/admin/reports");
  if (denied) return denied;
  const { key } = await params;
  if (!REPORT_KEY.test(key)) notFound();
  const found = await findReport(key);
  if (!found) {
    return (
      <ReportsShell active={key}>
        <EmptyState title="Report not available" description="It does not exist, or your role does not include it." />
      </ReportsShell>
    );
  }
  const { report, category } = found;
  const session = await requireStaff();
  const canDownload = hasPermission(session.permissions, Permission.REPORT_GENERATE)
    || hasPermission(session.permissions, Permission.REPORT_EXPORT);
  const state = readState(report, await searchParams);

  let result: ReportResult | null = null;
  let problem: string | null = null;
  try {
    result = await authFetch<ReportResult>(`/api/v1/admin/analytics/reports/${key}?${toQuery(state, {}, false)}`);
  } catch (error) {
    if (error instanceof BackendError && error.status >= 400 && error.status < 500) {
      problem = error.apiError?.message ?? "Check the filters.";
    } else throw error;
  }
  const base = `/admin/reports/${key}`;
  const href = (overrides: Partial<ReportState>) => {
    const query = toQuery(state, overrides).toString();
    return query ? `${base}?${query}` : base;
  };
  const exportHref = (format: string) => `/admin/reports/export?${new URLSearchParams([
    ["report", key], ["format", format], ...toQuery(state, { page: 0, size: 50 }, false)])}`;
  const columns = (result?.columns ?? report.columns).map((c, index) => ({ ...c, index }))
    .filter((c) => !state.hidden.includes(c.key));
  const currency = result?.totals.length === 1 ? result.totals[0]!.currency : CURRENCY;

  return (
    <ReportsShell active={key}>
      <PageHeader title={report.title} description={report.description}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Link href={href({})} aria-label="Refresh" title="Refresh"
              className="inline-flex size-9 items-center justify-center rounded-lg border border-border bg-surface text-muted transition-colors hover:border-gold hover:text-gold-text">
              <RefreshCw aria-hidden="true" className="size-4" />
            </Link>
            {canDownload && result && (
              <>
                <DownloadLink href={exportHref("xlsx")} format="xlsx" primary />
                <DownloadLink href={exportHref("pdf")} format="pdf" />
                <DownloadLink href={exportHref("csv")} format="csv" />
              </>
            )}
          </div>
        } />
      <p className="-mt-3 flex flex-wrap items-center gap-2 text-xs text-muted">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-background px-2.5 py-1 font-medium ring-1 ring-border">
          <FolderOpen aria-hidden="true" className="size-3.5 text-gold-text" /> {category.label}
        </span>
        {!canDownload && <span>Downloading needs the REPORT_GENERATE or REPORT_EXPORT permission.</span>}
      </p>

      <Filters report={report} state={state} base={base} />
      <Chips report={report} state={state} href={href} />

      {problem && <Notice tone="warning">{problem}</Notice>}
      {result && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
            <p className="flex flex-wrap items-center gap-x-2 text-muted">
              <span className="inline-flex items-center gap-1.5 font-medium text-foreground">
                <Rows3 aria-hidden="true" className="size-4 text-gold-text" />
                {new Intl.NumberFormat("en-IN").format(result.totalRows)} {result.totalRows === 1 ? "row" : "rows"}
              </span>
              {state.from && state.to && <> · {formatDate(state.from)} – {formatDate(state.to)}{report.dateLabel && ` by ${report.dateLabel.toLowerCase()}`}</>}
              {!report.usesPeriod && " · as things stand"}
            </p>
            <ColumnPicker columns={result.columns} state={state} href={href} />
          </div>
          {result.rows.length === 0 ? (
            <EmptyState title="Nothing matches" description="Try a longer period or fewer filters." />
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-border bg-surface shadow-sm">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-border bg-background/70 text-[11px] tracking-[0.06em] text-muted uppercase">
                  <tr>
                    {columns.map((c) => (
                      <th key={c.key} scope="col" className={`whitespace-nowrap px-3 py-3 font-medium ${numeric(c.type) ? "text-right" : ""}`}
                        aria-sort={sortedBy(report, state) === c.key ? (descending(report, state) ? "descending" : "ascending") : undefined}>
                        <SortHeader column={c} report={report} state={state} href={href} />
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {result.rows.map((row, r) => (
                    <tr key={r} className="transition-colors hover:bg-background/60">
                      {columns.map((c) => (
                        <td key={c.key} className={`whitespace-nowrap px-3 py-2 ${numeric(c.type) ? "text-right tabular-nums" : ""}`}>
                          <Value column={c} value={row[c.index] ?? null} target={result.links[r]?.[c.key]} currency={currency} />
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
                {result.totals.length > 0 && (
                  <tfoot className="border-t-2 border-border bg-gold/5 font-semibold">
                    {result.totals.map((t) => (
                      <tr key={t.currency}>
                        {columns.map((c, i) => (
                          <td key={c.key} className={`whitespace-nowrap px-3 py-2.5 ${numeric(c.type) ? "text-right tabular-nums" : ""}`}>
                            {i === 0 && t.values[c.key] === undefined
                              ? `Total${result.totals.length > 1 ? ` (${t.currency})` : ""} · all ${result.totalRows} rows`
                              : t.values[c.key] !== undefined ? formatCell(c.type, t.values[c.key]!, t.currency) : ""}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tfoot>
                )}
              </table>
            </div>
          )}
          <Pager result={result} href={href} />
          {result.note && <p className="text-xs text-muted">{result.note}</p>}
          <p className="text-xs text-muted">Generated {formatDateTime(result.generatedAt)}.</p>
        </>
      )}
    </ReportsShell>
  );
}

function sortedBy(report: CatalogEntry, state: ReportState) {
  return state.sort ?? report.defaultSort;
}

function descending(report: CatalogEntry, state: ReportState) {
  return state.desc ?? (state.sort ? false : report.defaultDescending);
}

function SortHeader({ column, report, state, href }: {
  column: ReportColumn; report: CatalogEntry; state: ReportState; href: (o: Partial<ReportState>) => string;
}) {
  if (!column.sortable) return <>{column.label}</>;
  const active = sortedBy(report, state) === column.key;
  const desc = descending(report, state);
  return (
    <Link href={href({ sort: column.key, desc: active ? !desc : numeric(column.type), page: 0 })}
      className={`group inline-flex items-center gap-1 hover:text-foreground ${active ? "text-gold-text" : ""}`}>
      {column.label}
      <span aria-hidden className={active ? "" : "opacity-0 group-hover:opacity-50"}>{active && desc ? "↓" : "↑"}</span>
    </Link>
  );
}

function Value({ column, value, target, currency }: {
  column: ReportColumn; value: string | number | null; target: string | undefined; currency: string;
}) {
  if (column.masked || value === "Restricted") {
    return <span className="text-xs text-muted" title="Your role does not include this detail">Restricted</span>;
  }
  const text = formatCell(column.type, value, currency);
  if (column.type === "STATUS" && value !== null) {
    const tone = /FAIL|BREACH|MISMATCH|REJECT|UNBALANCED|DISCREPANCY|MISSING|DUPLICATE|NOT_IN_LEDGER|LOST|CANCEL/.test(String(value)) ? "danger"
      : /PENDING|AWAITING|PROCESSING|REFUND_REQUIRED|OPEN|PARTLY|NO_INVOICE|MAINTENANCE/.test(String(value)) ? "warning"
      : /PAID|SUCCEEDED|ACTIVE|APPROVED|MATCHED|MET|CREDITED|WON|FULFILLED|ACCEPTED|ON_LEASE|COMPLETED|CONFIRMED/.test(String(value)) ? "success"
      : "neutral";
    return <StatusBadge tone={tone}>{text}</StatusBadge>;
  }
  const prefix = column.link ? LINKS[column.link] : undefined;
  if (prefix && target && value !== null) {
    return <Link href={`${prefix}${target}`} className="text-gold-text underline-offset-2 hover:underline">{text}</Link>;
  }
  if (column.type === "TEXT" && text.length > 36) {
    return <span className="block max-w-[18rem] truncate" title={text}>{text}</span>;
  }
  return <>{text}</>;
}

function Filters({ report, state, base }: { report: CatalogEntry; state: ReportState; base: string }) {
  const presetQuery = (from: string, to: string) => toQuery(state, { from, to, page: 0 }).toString();
  return (
    <section aria-label="Filters" className="space-y-4 rounded-2xl border border-border bg-surface p-4 shadow-sm sm:p-5">
      {report.usesPeriod && (
        <nav aria-label="Period" className="inline-flex max-w-full flex-wrap gap-1 rounded-xl border border-border bg-background/60 p-1 text-sm">
          {periodPresets().map((p) => {
            const active = p.from === state.from && p.to === state.to;
            return (
              <Link key={p.label} href={`${base}?${presetQuery(p.from, p.to)}`} aria-current={active ? "page" : undefined}
                className={pill(active)}>{p.label}</Link>
            );
          })}
          <Link href={`${base}?${toQuery(state, { from: undefined, to: undefined, page: 0 })}`} aria-current={!state.from ? "page" : undefined}
            className={pill(!state.from)}>All dates</Link>
        </nav>
      )}
      <form action={base} className="flex flex-wrap items-end gap-3 border-t border-border pt-4">
        {state.sort && <input type="hidden" name="sort" value={state.sort} />}
        {state.desc !== undefined && <input type="hidden" name="desc" value={String(state.desc)} />}
        {state.hidden.length > 0 && <input type="hidden" name="hide" value={state.hidden.join(",")} />}
        {report.usesPeriod && (
          <>
            <div className="w-40"><TextField label={`${report.dateLabel ?? "Date"} from`} name="from" type="date" defaultValue={state.from} /></div>
            <div className="w-40"><TextField label="to" name="to" type="date" defaultValue={state.to} /></div>
          </>
        )}
        {report.searchable && (
          <div className="w-56"><TextField label="Search" name="search" defaultValue={state.search} maxLength={100} placeholder="ID, name, reference…" /></div>
        )}
        {report.choices.map((choice) => (
          <details key={choice.key} className="relative">
            <summary className={`flex h-11 cursor-pointer list-none items-center gap-1.5 rounded-lg border px-3 text-sm font-medium transition-colors hover:border-gold [&::-webkit-details-marker]:hidden ${state.choices[choice.key]?.length
              ? "border-gold/50 bg-gold/5 text-gold-text" : "border-border bg-surface"}`}>
              {choice.label}
              {state.choices[choice.key]?.length ? <span className="rounded-full bg-gold px-1.5 text-[10px] font-semibold text-on-gold">{state.choices[choice.key]!.length}</span> : null}
              <ChevronDown aria-hidden="true" className="size-4 text-muted" />
            </summary>
            <fieldset className="absolute z-20 mt-1 max-h-72 w-64 space-y-0.5 overflow-y-auto rounded-xl border border-border bg-surface p-2 shadow-xl">
              <legend className="sr-only">{choice.label}</legend>
              {choice.values.map((v) => (
                <label key={v} className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-background">
                  <input type="checkbox" name={`f.${choice.key}`} value={v} defaultChecked={state.choices[choice.key]?.includes(v)} className="size-4 accent-[var(--gold)]" />
                  {humanize(v)}
                </label>
              ))}
            </fieldset>
          </details>
        ))}
        {report.amountFilter && (
          <>
            <div className="w-32"><TextField label="Amount from (₹)" name="min" inputMode="decimal" defaultValue={state.min} /></div>
            <div className="w-32"><TextField label="to (₹)" name="max" inputMode="decimal" defaultValue={state.max} /></div>
          </>
        )}
        <label className="text-sm">
          <span className="block font-medium">Rows per page</span>
          <select name="size" defaultValue={String(state.size)} className="mt-1 h-11 rounded-lg border border-border bg-surface px-2 outline-none focus:border-gold">
            {[25, 50, 100, 200].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <Button type="submit" className="gap-2"><Filter aria-hidden="true" className="size-4" /> Apply</Button>
        <Link href={base} className="inline-flex h-11 items-center gap-1.5 rounded-lg px-3 text-sm text-muted transition-colors hover:bg-background hover:text-foreground">
          <X aria-hidden="true" className="size-4" /> Clear all
        </Link>
      </form>
    </section>
  );
}

function Chips({ report, state, href }: { report: CatalogEntry; state: ReportState; href: (o: Partial<ReportState>) => string }) {
  const chips: { label: string; remove: string }[] = [];
  for (const choice of report.choices) {
    for (const v of state.choices[choice.key] ?? []) {
      const rest = { ...state.choices, [choice.key]: state.choices[choice.key]!.filter((x) => x !== v) };
      chips.push({ label: `${choice.label}: ${humanize(v)}`, remove: href({ choices: rest, page: 0 }) });
    }
  }
  if (state.search) chips.push({ label: `Search: ${state.search}`, remove: href({ search: undefined, page: 0 }) });
  if (state.min) chips.push({ label: `From ₹${state.min}`, remove: href({ min: undefined, page: 0 }) });
  if (state.max) chips.push({ label: `Up to ₹${state.max}`, remove: href({ max: undefined, page: 0 }) });
  if (chips.length === 0) return null;
  return (
    <ul aria-label="Filters applied" className="flex flex-wrap gap-2">
      {chips.map((c) => (
        <li key={c.label} className={chip}>
          {c.label}
          <Link href={c.remove} aria-label={`Remove ${c.label}`}
            className="flex size-5 items-center justify-center rounded-full transition-colors hover:bg-gold hover:text-on-gold">
            <X aria-hidden="true" className="size-3" />
          </Link>
        </li>
      ))}
    </ul>
  );
}

function ColumnPicker({ columns, state, href }: { columns: ReportColumn[]; state: ReportState; href: (o: Partial<ReportState>) => string }) {
  return (
    <details className="relative">
      <summary className="inline-flex min-h-8 cursor-pointer list-none items-center gap-1.5 rounded-lg border border-border bg-surface px-3 text-xs font-medium text-muted transition-colors hover:border-gold hover:text-foreground [&::-webkit-details-marker]:hidden">
        <Columns3 aria-hidden="true" className="size-3.5" />
        Columns{state.hidden.length ? ` (${state.hidden.length} hidden)` : ""}
      </summary>
      <ul className="absolute right-0 z-20 mt-1 max-h-80 w-60 space-y-0.5 overflow-y-auto rounded-xl border border-border bg-surface p-2 text-sm shadow-xl">
        {columns.map((c) => {
          const hidden = state.hidden.includes(c.key);
          const next = hidden ? state.hidden.filter((k) => k !== c.key) : [...state.hidden, c.key];
          return (
            <li key={c.key}>
              <Link href={href({ hidden: next })} className={`flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-background ${hidden ? "text-muted" : ""}`} aria-pressed={!hidden}>
                <span aria-hidden className={`flex size-4 items-center justify-center rounded ${hidden ? "ring-1 ring-border" : "bg-gold text-on-gold"}`}>
                  {!hidden && <Check className="size-3" />}
                </span>
                {c.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </details>
  );
}

function Pager({ result, href }: { result: ReportResult; href: (o: Partial<ReportState>) => string }) {
  const pages = Math.max(1, Math.ceil(result.totalRows / result.size));
  return (
    <PagerLinks page={result.page} pages={pages}
      prev={result.page > 0 ? href({ page: result.page - 1 }) : null}
      next={result.page + 1 < pages ? href({ page: result.page + 1 }) : null} />
  );
}
