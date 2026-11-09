import Link from "next/link";
import { Banknote, Bot, ChevronRight, Eye, KeyRound, type LucideIcon, Settings2, ShieldCheck, UserCheck } from "lucide-react";
import { EmailText } from "@/components/ui/email-text";
import { DISPLAY_TIME_ZONE, TIME_ZONE_LABEL } from "@/lib/time-zone";
import type { AuditCategory, AuditLogEntry } from "@/types/security";
import { formatDate } from "@/utils/format";
import { EntityLink } from "./audit-table";
import { CATEGORY_LABEL, actionLabel, actionTone, personLabel } from "./labels";

export const CATEGORY_ICON: Record<AuditCategory, LucideIcon> = {
  SIGN_IN: KeyRound,
  ACCESS_CONTROL: ShieldCheck,
  SENSITIVE_DATA: Eye,
  VERIFICATION: UserCheck,
  MONEY: Banknote,
  OPERATIONS: Settings2,
};

const TONE = {
  danger: { tile: "bg-rose-500/10 text-rose-600 dark:text-rose-400", label: "text-rose-700 dark:text-rose-300", edge: "border-l-rose-500" },
  warning: { tile: "bg-amber-500/10 text-amber-700 dark:text-amber-300", label: "text-amber-800 dark:text-amber-200", edge: "border-l-amber-500" },
  neutral: { tile: "bg-gold/10 text-gold-text", label: "", edge: "border-l-transparent" },
} as const;

const time = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: DISPLAY_TIME_ZONE });

/** Audit entries, newest first, grouped by day; each row opens the entry. */
export function AuditList({ entries, showIp = true }: { entries: readonly AuditLogEntry[]; showIp?: boolean }) {
  const days: { day: string; items: AuditLogEntry[] }[] = [];
  for (const entry of entries) {
    const day = formatDate(entry.createdAt);
    const last = days.at(-1);
    if (last?.day === day) last.items.push(entry);
    else days.push({ day, items: [entry] });
  }
  return (
    <div className="space-y-5">
      {days.map(({ day, items }) => (
        <section key={day} aria-label={day} className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          <h3 className="flex items-center justify-between gap-3 border-b border-border bg-background/40 px-4 py-2.5 text-sm font-semibold">
            {day}
            <span className="text-xs font-normal text-muted">{items.length} {items.length === 1 ? "entry" : "entries"}</span>
          </h3>
          <ul className="divide-y divide-border">
            {items.map((entry) => {
              const tone = TONE[actionTone(entry.action)];
              const Icon = CATEGORY_ICON[entry.category] ?? Settings2;
              return (
                <li key={entry.id} className={`grid gap-x-4 gap-y-1 border-l-2 px-4 py-3 text-sm sm:grid-cols-[3.5rem_minmax(0,1fr)_auto] sm:items-center ${tone.edge}`}>
                  <span className="hidden text-xs text-muted tabular-nums sm:block" title={TIME_ZONE_LABEL}>{time.format(new Date(entry.createdAt))}</span>
                  <div className="flex min-w-0 items-start gap-3">
                    <span aria-hidden="true" title={CATEGORY_LABEL[entry.category]}
                      className={`mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg ${tone.tile}`}>
                      <Icon className="size-4" />
                    </span>
                    <div className="min-w-0">
                      <p className={`font-medium ${tone.label}`}>
                        <Link href={`/platform/audit/${entry.id}`} className="card-link hover:underline">{actionLabel(entry.action)}</Link>
                        <span className="ml-2 text-xs font-normal text-muted tabular-nums sm:hidden">{time.format(new Date(entry.createdAt))}</span>
                      </p>
                      <p className="flex min-w-0 items-center gap-1 text-xs whitespace-nowrap text-muted">
                        <span className="min-w-0 shrink truncate [&_wbr]:hidden">
                          {entry.actor ? (
                            <Link href={`/platform/users/${entry.actor.id}`} className="font-medium text-foreground hover:text-gold-text" title={personLabel(entry.actor)}>
                              {entry.actor.email ? <EmailText>{entry.actor.email}</EmailText> : "Deleted account"}
                            </Link>
                          ) : (
                            <span className="inline-flex items-center gap-1"><Bot aria-hidden="true" className="size-3" />
                              {entry.action === "LOGIN_FAILED" ? "Unknown visitor" : "System"}</span>
                          )}
                        </span>
                        <span aria-hidden="true" className="shrink-0">→</span><span className="sr-only"> about </span>
                        <span className="min-w-0 shrink-[2] truncate"><EntityLink entry={entry} /></span>
                      </p>
                    </div>
                  </div>
                  <div className="hidden items-center gap-2 sm:flex">
                    {showIp && entry.ipAddress && (
                      <span className="rounded-md bg-background px-1.5 py-0.5 font-mono text-[11px] text-muted ring-1 ring-border">{entry.ipAddress}</span>
                    )}
                    <ChevronRight aria-hidden="true" className="hidden size-4 text-muted sm:block" />
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}
