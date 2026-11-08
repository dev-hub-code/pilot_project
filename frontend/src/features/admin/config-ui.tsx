import type { LucideIcon } from "lucide-react";
import { History } from "lucide-react";

/** A settings card with an icon heading, used across Platform config. */
export function ConfigPanel({ icon: Icon, title, description, actions, children, className = "" }: {
  icon: LucideIcon;
  title: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm ${className}`}>
      <div className="flex flex-wrap items-start gap-3">
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
          <Icon className="size-[18px]" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="font-semibold tracking-tight">{title}</h2>
          {description && <p className="text-xs text-muted">{description}</p>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  );
}

/** A side card of short points explaining how a setting behaves. */
export function HowItWorks({ points, title = "How it works" }: { points: readonly { icon: LucideIcon; text: React.ReactNode }[]; title?: string }) {
  return (
    <aside className="h-fit space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm lg:sticky lg:top-6">
      <h2 className="font-semibold">{title}</h2>
      <ul className="space-y-3 text-sm">
        {points.map(({ icon: Icon, text }, i) => (
          <li key={i} className="flex gap-3">
            <span aria-hidden="true" className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text">
              <Icon className="size-4" />
            </span>
            <span className="pt-0.5 text-muted">{text}</span>
          </li>
        ))}
      </ul>
    </aside>
  );
}

/** A small figure tile: icon, label and value. */
export function ConfigStat({ icon: Icon, label, value, hint }: { icon: LucideIcon; label: string; value: React.ReactNode; hint?: React.ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-xl bg-background/60 p-3 ring-1 ring-border">
      <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface text-gold-text ring-1 ring-border">
        <Icon className="size-4" />
      </span>
      <div className="min-w-0">
        <p className="text-[11px] text-muted">{label}</p>
        <p className="text-sm font-semibold tabular-nums break-words">{value}</p>
        {hint && <p className="text-[11px] text-muted">{hint}</p>}
      </div>
    </div>
  );
}

/** "Last changed …" or "The default applies …" under a setting. */
export function ChangedNote({ children }: { children: React.ReactNode }) {
  return (
    <p className="inline-flex items-center gap-1.5 text-xs text-muted">
      <History aria-hidden="true" className="size-3.5 shrink-0" /> {children}
    </p>
  );
}
