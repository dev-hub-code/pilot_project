import type { Metadata } from "next";
import {
  CalendarClock, CalendarPlus, CheckCircle2, Globe, Handshake, History, Lock, type LucideIcon, Network, XCircle,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { ConfigPanel, HowItWorks } from "@/features/admin/config-ui";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { cancelRatesAction } from "@/features/admin/referral-actions";
import { ScheduleRatesForm } from "@/features/admin/referral-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { RateState, RateVersion } from "@/types/referral";
import { formatDateTime, humanize } from "@/utils/format";
import { formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referral rates" };

export default async function ReferralRatesPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.FINANCE_VIEW) && !can(Permission.REFERRAL_CONFIG_MANAGE)) {
    return <EmptyState title="Not available" description="Referral rates are for finance and admins." />;
  }
  const versions = await authFetch<RateVersion[]>("/api/v1/admin/referral-rates");
  const current = versions.find((v) => v.state === "IN_FORCE");
  const canManage = can(Permission.REFERRAL_CONFIG_MANAGE);

  const scheduled = versions.filter((v) => v.state === "SCHEDULED");

  return (
    <div className="space-y-6">
      <section className="relative isolate overflow-hidden rounded-3xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20">
        <div aria-hidden="true" className="absolute -top-24 -right-20 -z-10 size-64 rounded-full bg-gold/25 blur-3xl" />
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-gold text-on-gold">
              <Handshake className="size-5" />
            </span>
            <div>
              <p className="text-xs tracking-[0.08em] text-on-ink/60 uppercase">Rates in force</p>
              <p className="text-sm text-on-ink/80">{current ? `Since ${formatDateTime(current.effectiveFrom)}` : "None set yet"}</p>
            </div>
          </div>
          {scheduled.length > 0 && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs text-gold-on-ink">
              <CalendarClock aria-hidden="true" className="size-3.5" /> {scheduled.length} change{scheduled.length === 1 ? "" : "s"} scheduled
            </span>
          )}
        </div>
        {current && (
          <>
            <ul className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-5">
              {current.percents.map((p, i) => (
                <li key={i} className="rounded-2xl bg-white/10 p-3">
                  <p className="text-xs text-on-ink/60">Network · level {i + 1}</p>
                  <p className="mt-1 text-xl font-semibold tabular-nums">{formatPercent(p, 3)}</p>
                </li>
              ))}
              <li className="col-span-2 rounded-2xl bg-gold/15 p-3 ring-1 ring-gold/30 sm:col-span-1">
                <p className="text-xs text-gold-on-ink">Online scheme · once</p>
                <p className="mt-1 text-xl font-semibold tabular-nums">{formatPercent(current.oneTimePercent, 3)}</p>
              </li>
            </ul>
            {current.reason && <p className="mt-4 text-xs text-on-ink/60">“{current.reason}”</p>}
          </>
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-6">
          {canManage && (
            <ConfigPanel icon={CalendarPlus} title="Schedule new rates"
              description="Rates in force are never edited: new rates start now or later and apply to payouts made from then on.">
              <ScheduleRatesForm current={current?.percents ?? []} currentOneTime={current?.oneTimePercent} />
            </ConfigPanel>
          )}

          <section aria-labelledby="versions-title" className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
            <h2 id="versions-title" className="flex items-center gap-3 border-b border-border bg-background/40 px-5 py-4 font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                <History className="size-[18px]" />
              </span>
              History <span className="text-sm font-normal text-muted">{versions.length}</span>
            </h2>
            {versions.length === 0 ? <div className="p-5"><EmptyState title="No rates yet" /></div> : (
              <ol className="divide-y divide-border">
                {versions.map((v) => {
                  const style = STATE_STYLE[v.state];
                  return (
                    <li key={v.id} className={`grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center ${v.state === "SUPERSEDED" || v.state === "CANCELLED" ? "opacity-70" : ""}`}>
                      <div className="min-w-0 space-y-2">
                        <p className="flex flex-wrap items-center gap-2 text-sm">
                          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${style.chip}`}>
                            <style.icon aria-hidden="true" className="size-3" /> {humanize(v.state)}
                          </span>
                          <span className="font-medium">From {formatDateTime(v.effectiveFrom)}</span>
                        </p>
                        <ul className="flex flex-wrap gap-1.5 text-xs">
                          {v.percents.map((p, i) => (
                            <li key={i} className="rounded-lg bg-background px-2 py-1 tabular-nums ring-1 ring-border">
                              <span className="text-muted">L{i + 1}</span> <span className="font-semibold">{formatPercent(p, 3)}</span>
                            </li>
                          ))}
                          <li className="rounded-lg bg-gold/10 px-2 py-1 tabular-nums text-gold-text">
                            Once <span className="font-semibold">{formatPercent(v.oneTimePercent, 3)}</span>
                          </li>
                        </ul>
                        {v.reason && <p className="text-xs break-words text-muted">{v.reason}</p>}
                      </div>
                      {v.state === "SCHEDULED" && canManage && (
                        <ConfirmForm action={cancelRatesAction.bind(null, v.id)} submitLabel="Cancel" variant="secondary"
                          confirm={`Cancel the rates scheduled for ${formatDateTime(v.effectiveFrom)}?`} />
                      )}
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        </div>

        <HowItWorks points={[
          { icon: Network, text: "Network investors earn each level's rate of their downline's investment, every term month. Levels 5 and below earn nothing." },
          { icon: Globe, text: "Online investors earn the one-time rate once, on the first order of each person they refer directly." },
          { icon: Lock, text: "Rates in force are never edited. Schedule new ones instead." },
          { icon: CalendarClock, text: "A scheduled change can be cancelled until it takes effect." },
        ]} />
      </div>
    </div>
  );
}

const STATE_STYLE: Record<RateState, { chip: string; icon: LucideIcon }> = {
  IN_FORCE: { chip: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300", icon: CheckCircle2 },
  SCHEDULED: { chip: "bg-amber-500/10 text-amber-700 dark:text-amber-300", icon: CalendarClock },
  SUPERSEDED: { chip: "bg-background text-muted ring-1 ring-border", icon: History },
  CANCELLED: { chip: "bg-red-500/10 text-red-700 dark:text-red-300", icon: XCircle },
};
