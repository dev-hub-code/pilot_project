import {
  Ban, BadgeCheck, Banknote, CircleCheck, CircleDot, CircleX, FileSearch, Hourglass, Landmark,
  PauseCircle, RotateCcw, Send, ShieldCheck, type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import type { Money } from "@/types/plan";
import type { WithdrawalEvent, WithdrawalStatus } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { WITHDRAWAL_STATUS_LABEL } from "./labels";

/** An icon and colour per status, shared by the timeline, the stepper and the queue. */
export const STATUS_ICON: Record<WithdrawalStatus, { icon: LucideIcon; tone: string }> = {
  SUBMITTED: { icon: Send, tone: "text-gold-text bg-gold/10" },
  UNDER_REVIEW: { icon: FileSearch, tone: "text-amber-700 bg-amber-500/10 dark:text-amber-400" },
  ON_HOLD: { icon: PauseCircle, tone: "text-amber-700 bg-amber-500/10 dark:text-amber-400" },
  PENDING_APPROVAL: { icon: ShieldCheck, tone: "text-amber-700 bg-amber-500/10 dark:text-amber-400" },
  APPROVED: { icon: BadgeCheck, tone: "text-emerald-700 bg-emerald-500/10 dark:text-emerald-400" },
  BATCHED: { icon: Landmark, tone: "text-gold-text bg-gold/10" },
  PROCESSING: { icon: Hourglass, tone: "text-gold-text bg-gold/10" },
  PAID: { icon: CircleCheck, tone: "text-emerald-700 bg-emerald-500/10 dark:text-emerald-400" },
  FAILED: { icon: CircleX, tone: "text-rose-700 bg-rose-500/10 dark:text-rose-400" },
  RETURNED: { icon: RotateCcw, tone: "text-slate-600 bg-slate-500/10 dark:text-slate-300" },
  REJECTED: { icon: Ban, tone: "text-rose-700 bg-rose-500/10 dark:text-rose-400" },
  CANCELLED: { icon: Ban, tone: "text-slate-600 bg-slate-500/10 dark:text-slate-300" },
};

/** A headline figure with an icon. */
export function MetricCard({ icon: Icon, label, value, note, accent = "text-gold-text bg-gold/10" }: {
  icon: LucideIcon;
  label: string;
  value: ReactNode;
  note?: ReactNode;
  accent?: string;
}) {
  return (
    <div className="group relative flex items-start gap-4 overflow-hidden rounded-2xl border border-border bg-surface p-5 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-lg hover:shadow-gold/10">
      <span aria-hidden="true" className="absolute inset-x-0 top-0 h-0.5 origin-left scale-x-0 bg-gold transition-transform duration-500 group-hover:scale-x-100" />
      <span className={`flex size-11 shrink-0 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-105 ${accent}`}>
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <div className="min-w-0 space-y-0.5">
        <p className="text-sm text-muted">{label}</p>
        <p className="font-display text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
        {note && <p className="text-xs text-muted">{note}</p>}
      </div>
    </div>
  );
}

/** Label/value rows for an amount breakdown; the {@code strong} row is the total. */
export function Breakdown({ rows }: { rows: { label: string; value: ReactNode; strong?: boolean; muted?: boolean }[] }) {
  return (
    <dl className="divide-y divide-border overflow-hidden rounded-lg border border-border text-sm">
      {rows.map((r) => (
        <div key={r.label} className={`flex items-center justify-between gap-4 px-4 py-3 ${r.strong ? "bg-gold/5" : ""}`}>
          <dt className={r.strong ? "font-medium" : "text-muted"}>{r.label}</dt>
          <dd className={`text-right tabular-nums ${r.strong ? "text-lg font-semibold" : r.muted ? "text-muted" : ""}`}>{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** "− ₹10.00", or "None" for zero. */
export function deduction(m: Money): string {
  return Number(m.amount) === 0 ? "None" : `− ${formatMoney(m)}`;
}

/** The milestones of a withdrawal, with when each was reached (from its timeline). */
export function ProgressStepper({ status, timeline }: { status: WithdrawalStatus; timeline: WithdrawalEvent[] }) {
  const reachedAt = (statuses: WithdrawalStatus[]) => timeline.find((e) => statuses.includes(e.to))?.at ?? null;
  const steps: { label: string; statuses: WithdrawalStatus[]; icon: LucideIcon }[] = [
    { label: "Submitted", statuses: ["SUBMITTED", "APPROVED"], icon: Send },
    { label: "Under review", statuses: ["UNDER_REVIEW", "PENDING_APPROVAL", "ON_HOLD"], icon: FileSearch },
    { label: "Approved", statuses: ["APPROVED"], icon: BadgeCheck },
    { label: "Payment initiated", statuses: ["BATCHED", "PROCESSING"], icon: Landmark },
    { label: "Paid", statuses: ["PAID"], icon: Banknote },
  ];
  const order: WithdrawalStatus[] = ["SUBMITTED", "UNDER_REVIEW", "APPROVED", "BATCHED", "PAID"];
  const rank: Partial<Record<WithdrawalStatus, number>> = {
    SUBMITTED: 0, UNDER_REVIEW: 1, ON_HOLD: 1, PENDING_APPROVAL: 1, APPROVED: 2, BATCHED: 3, PROCESSING: 3, FAILED: 3, PAID: 4,
  };
  const current = rank[status] ?? 0;
  const failed = status === "FAILED";
  return (
    <ol className="grid grid-cols-5 gap-1" aria-label="Progress">
      {steps.map((step, i) => {
        const done = i < current || (i === current && status === order[4]);
        const active = i === current && !done;
        const Icon = failed && i === 4 ? CircleX : step.icon;
        const at = done || active ? reachedAt(i === 0 ? ["SUBMITTED", "APPROVED", "BATCHED"] : step.statuses) : null;
        return (
          <li key={step.label} className="relative flex flex-col items-center text-center" aria-current={active ? "step" : undefined}>
            {i > 0 && (
              <span aria-hidden="true" className={`absolute top-5 right-1/2 h-0.5 w-full -translate-y-1/2 ${i <= current ? "bg-gold" : "bg-border"}`} />
            )}
            <span className={`relative z-10 flex size-10 items-center justify-center rounded-full border-2 ${
              failed && i === 4 ? "border-rose-500 bg-rose-50 text-rose-600 dark:bg-rose-500/10"
                : done ? "border-gold bg-gold text-on-gold"
                  : active ? "border-gold bg-surface text-gold-text ring-4 ring-gold/15"
                    : "border-border bg-surface text-muted"}`}>
              <Icon className="size-4.5" aria-hidden="true" />
            </span>
            <span className={`mt-2 text-xs ${done || active ? "font-medium text-foreground" : "text-muted"}`}>
              {failed && i === 4 ? "Payment failed" : step.label}
            </span>
            {at && <span className="hidden text-[11px] text-muted sm:block">{formatDateTime(at)}</span>}
          </li>
        );
      })}
    </ol>
  );
}

/** Every step, newest first, with an icon; staff see who made it and the internal steps. */
export function Timeline({ events, staff = false, label = (s: WithdrawalStatus) => WITHDRAWAL_STATUS_LABEL[s] }: {
  events: WithdrawalEvent[];
  staff?: boolean;
  label?: (status: WithdrawalStatus) => string;
}) {
  if (events.length === 0) return <p className="text-sm text-muted">Nothing recorded yet.</p>;
  const newestFirst = [...events].reverse();
  return (
    <ol className="space-y-0">
      {newestFirst.map((e, i) => {
        const { icon: Icon, tone } = STATUS_ICON[e.to] ?? { icon: CircleDot, tone: "text-muted bg-background" };
        return (
          <li key={`${e.at}-${i}`} className="relative flex gap-4 pb-6 last:pb-0">
            {i < newestFirst.length - 1 && <span aria-hidden="true" className="absolute top-10 bottom-0 left-[18px] w-px bg-border" />}
            <span className={`relative flex size-9 shrink-0 items-center justify-center rounded-full ${tone}`}>
              <Icon className="size-4" aria-hidden="true" />
            </span>
            <div className="min-w-0 pt-1.5">
              <p className="text-sm font-medium">
                {label(e.to)}
                {staff && !e.visibleToInvestor && (
                  <span className="ml-2 rounded bg-background px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-muted uppercase">Internal</span>
                )}
              </p>
              <p className="text-xs text-muted">
                {formatDateTime(e.at)}{staff && <> · {e.actorName || (e.actorId ? "Staff" : "System")}</>}
              </p>
              {e.reason && <p className="mt-1.5 rounded-lg bg-background px-3 py-2 text-sm text-muted">{e.reason}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/** "3h", "2d": how long since. */
export function age(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000));
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

/** Whether it has waited more than {@code days} (shown as overdue in the queue). */
export function olderThanDays(iso: string, days: number): boolean {
  return Date.now() - new Date(iso).getTime() > days * 86_400_000;
}
