import Link from "next/link";
import { ChevronLeft, ChevronRight, FileDown, FileSpreadsheet, FileText } from "lucide-react";

const FORMAT_ICON = { xlsx: FileSpreadsheet, pdf: FileText, csv: FileDown } as const;
const FORMAT_LABEL = { xlsx: "Excel", pdf: "PDF", csv: "CSV" } as const;

/** A download button for one file format; the first one offered is the strong one. */
export function DownloadLink({ href, format, primary = false, label }: {
  href: string;
  format: keyof typeof FORMAT_ICON;
  primary?: boolean;
  label?: string;
}) {
  const Icon = FORMAT_ICON[format];
  return (
    <a href={href}
      className={`inline-flex min-h-9 items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition-colors ${primary
        ? "bg-ink text-on-ink shadow-sm hover:opacity-90 dark:bg-gold dark:text-on-gold"
        : "border border-border bg-surface text-foreground hover:border-gold hover:text-gold-text"}`}>
      <Icon aria-hidden="true" className="size-4" /> {label ?? FORMAT_LABEL[format]}
    </a>
  );
}

/** Previous / next for a paged report, with where you are. */
export function PagerLinks({ page, pages, prev, next, summary }: {
  page: number;
  pages: number;
  prev: string | null;
  next: string | null;
  summary?: string;
}) {
  if (pages <= 1) return null;
  const button = "inline-flex min-h-9 items-center gap-1 rounded-lg border border-border bg-surface px-3 text-sm font-medium shadow-sm transition-colors hover:border-gold hover:text-gold-text";
  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center justify-between gap-3 text-sm">
      <span className="text-muted">Page {page + 1} of {pages}{summary ? ` · ${summary}` : ""}</span>
      <span className="flex gap-2">
        {prev && <Link className={button} href={prev}><ChevronLeft aria-hidden="true" className="size-4" /> Previous</Link>}
        {next && <Link className={button} href={next}>Next <ChevronRight aria-hidden="true" className="size-4" /></Link>}
      </span>
    </nav>
  );
}

/** Pill-tab classes, the same as elsewhere in admin. */
export function pill(active: boolean): string {
  return `rounded-lg px-3 py-1.5 font-medium whitespace-nowrap transition-colors duration-200 ${active
    ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
    : "text-muted hover:bg-surface hover:text-foreground"}`;
}
