import { CalendarClock, Clock, Globe, History, Lock, RefreshCw, Timer, TrendingUp } from "lucide-react";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { RentCreditSettings } from "@/types/earning";
import { formatDateTime } from "@/utils/format";
import { HowItWorks } from "./config-ui";
import { RentCreditIntervalForm } from "./rent-credit-form";

const POINTS = [
  { icon: TrendingUp, text: "Returns build up all the time; they reach investors' wallets when each interval ends." },
  { icon: Globe, text: "Intervals are counted in UTC, so a daily credit lands at midnight UTC." },
  { icon: RefreshCw, text: "Changing it loses or repeats nothing: everything built up since the last credit goes at the next boundary of the new interval." },
  { icon: Timer, text: "Terms still start at the full hour after acceptance." },
];

/** How often rent is credited: editable by those who run payouts (admins) or manage system settings. */
export async function RentCreditCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<RentCreditSettings>("/api/v1/admin/settings/rent-credit");
  const defaultLabel = settings.options.find((o) => o.minutes === settings.defaultMinutes)?.label
    ?? `every ${settings.defaultMinutes} minutes`;
  const canEdit = hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE)
    || hasPermission(permissions, Permission.PAYOUT_PROCESS);
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="min-w-0 space-y-6">
        <section className="relative isolate overflow-hidden rounded-3xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20">
          <div aria-hidden="true" className="absolute -top-20 -right-16 -z-10 size-56 rounded-full bg-gold/25 blur-3xl" />
          <div className="flex flex-wrap items-center gap-4">
            <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gold text-on-gold">
              <CalendarClock className="size-6" />
            </span>
            <div className="min-w-0">
              <p className="text-xs tracking-[0.08em] text-on-ink/60 uppercase">Returns are credited</p>
              <p className="text-2xl font-semibold tracking-tight">{settings.label.charAt(0).toUpperCase() + settings.label.slice(1)}</p>
            </div>
          </div>
          <p className="mt-4 inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs text-on-ink/80">
            <History aria-hidden="true" className="size-3.5 text-gold-on-ink" />
            {settings.updatedAt
              ? `Last changed ${formatDateTime(settings.updatedAt)}${settings.updatedByName ? ` by ${settings.updatedByName}` : ""}`
              : `The default (${defaultLabel}) applies until it is changed here`}
          </p>
        </section>

        <section className="space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <div className="flex items-start gap-3">
            <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
              <Clock className="size-[18px]" />
            </span>
            <div className="min-w-0">
              <h2 className="font-semibold">How often returns are credited</h2>
              <p className="text-xs text-muted">Pick an interval and save. It applies from the next run.</p>
            </div>
          </div>
          {canEdit ? (
            <RentCreditIntervalForm intervalMinutes={settings.intervalMinutes} options={settings.options} />
          ) : (
            <p className="flex items-center gap-2 rounded-xl bg-background/60 px-4 py-3 text-sm text-muted ring-1 ring-border">
              <Lock aria-hidden="true" className="size-4 shrink-0" /> Only admins change this.
            </p>
          )}
        </section>
      </div>

      <HowItWorks points={POINTS} />
    </div>
  );
}
