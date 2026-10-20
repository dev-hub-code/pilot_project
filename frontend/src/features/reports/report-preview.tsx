import { EmailText } from "@/components/ui/email-text";
import { todayInZone } from "@/lib/time-zone";
import type { ReportDocument } from "@/types/report";

/** A report document on screen, section by section; the PDF uses the same data. */
export function ReportPreview({ report }: { report: ReportDocument }) {
  return (
    <div className="space-y-6">
      {report.sections.map((section, i) => (
        <section key={`${i}-${section.name}`} className="space-y-2">
          <h2 className="text-lg font-semibold tracking-tight">{section.name}</h2>
          <div className="overflow-x-auto border border-border bg-surface">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-[0.08em] text-muted">
                <tr>
                  {section.headers.map((h, c) => (
                    <th key={c} scope="col" className={`whitespace-nowrap px-4 py-2 font-medium ${c === 3 ? "text-right" : ""}`}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {section.rows.length === 0 ? (
                  <tr><td colSpan={4} className="px-4 py-3 text-muted">Nothing in this period.</td></tr>
                ) : section.rows.map((row, r) => (
                  <tr key={r} className={row.emphasis ? "font-semibold" : ""}>
                    <td className="whitespace-nowrap px-4 py-2"><EmailText>{row.c1}</EmailText></td>
                    <td className="px-4 py-2"><EmailText>{row.c2}</EmailText></td>
                    <td className="px-4 py-2 text-muted"><EmailText>{row.c3}</EmailText></td>
                    <td className="whitespace-nowrap px-4 py-2 text-right tabular-nums">{row.c4}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
      {report.footnote && <p className="text-xs text-muted">{report.footnote}</p>}
    </div>
  );
}

/** Today in the display time zone, as a date at UTC midnight (so the date arithmetic below stays on that day). */
function zoneToday(): Date {
  return new Date(`${todayInZone()}T00:00:00Z`);
}

/** The default period: the current month so far, in the display time zone. */
export function thisMonth(today = zoneToday()): { from: string; to: string } {
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return { from: iso(new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1))), to: iso(today) };
}

/** "This month" style presets as from/to ISO dates, in the display time zone. */
export function periodPresets(today = zoneToday()): { label: string; from: string; to: string }[] {
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  const y = today.getUTCFullYear();
  const m = today.getUTCMonth();
  const startOfMonth = new Date(Date.UTC(y, m, 1));
  const lastMonthStart = new Date(Date.UTC(y, m - 1, 1));
  const lastMonthEnd = new Date(Date.UTC(y, m, 0));
  return [
    { label: "This month", from: iso(startOfMonth), to: iso(today) },
    { label: "Last month", from: iso(lastMonthStart), to: iso(lastMonthEnd) },
    { label: "Year to date", from: iso(new Date(Date.UTC(y, 0, 1))), to: iso(today) },
    { label: "Last year", from: iso(new Date(Date.UTC(y - 1, 0, 1))), to: iso(new Date(Date.UTC(y - 1, 11, 31))) },
  ];
}
