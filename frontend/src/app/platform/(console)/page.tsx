import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowUpRight, Briefcase, Handshake, Headset, Landmark, LayoutGrid, type LucideIcon, Network, ShieldCheck, Target, Users,
} from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { EmptyState } from "@/components/ui/empty-state";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DashboardSection } from "@/types/admin-portal";
import type { Profile } from "@/types/user";

export const metadata: Metadata = { title: "Dashboard" };

/** Each dashboard section's icon, by the key the API sends; anything new falls back to a grid. */
const SECTION_ICON: Record<string, LucideIcon> = {
  platform: LayoutGrid,
  investors: Users,
  investments: Briefcase,
  finance: Landmark,
  compliance: ShieldCheck,
  support: Headset,
  sales: Target,
  partners: Network,
};

/** Sections follow the viewer's permissions: the API sends only what they may see. */
export default async function AdminDashboardPage() {
  const session = await requireStaff();
  const [sections, me] = await Promise.all([
    authFetch<DashboardSection[]>("/api/v1/admin/dashboard"),
    // Only for the greeting.
    authFetch<Profile>("/api/v1/users/me").catch(() => null),
  ]);
  const today = new Date().toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long", timeZone: "Asia/Kolkata" });
  const metricCount = sections.reduce((sum, s) => sum + s.metrics.length, 0);

  return (
    <div className="space-y-10">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="admin-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="terminal-above" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_50%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="flex flex-wrap items-end justify-between gap-6 p-6 sm:p-8 lg:p-10">
          <div className="space-y-3">
            <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">{today}</p>
            <h1 id="admin-title" className="text-3xl font-semibold tracking-tight sm:text-4xl">
              {me?.firstName ? <>Welcome back, <span className="text-gold-on-ink">{me.firstName}</span></> : "Dashboard"}
            </h1>
            <p className="flex flex-wrap items-center gap-2 text-sm text-on-ink/75">
              Signed in as
              {session.roles.map((role) => (
                <span key={role} className="rounded-full border border-on-ink/20 bg-on-ink/10 px-2.5 py-0.5 text-xs font-medium text-on-ink backdrop-blur-md">
                  {role}
                </span>
              ))}
            </p>
          </div>
          {sections.length > 0 && (
            <dl className="grid grid-cols-2 gap-3">
              <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-5 py-3 backdrop-blur-md">
                <dt className="text-xs text-on-ink/60">Areas</dt>
                <dd className="font-display text-2xl font-semibold tabular-nums">{sections.length}</dd>
              </div>
              <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-5 py-3 backdrop-blur-md">
                <dt className="text-xs text-on-ink/60">Figures</dt>
                <dd className="font-display text-2xl font-semibold tabular-nums">{metricCount}</dd>
              </div>
            </dl>
          )}
        </div>
      </section>

      {sections.length === 0 && <EmptyState title="Nothing to show yet" description="Your role has no dashboard data." />}

      {sections.map((section) => {
        const Icon = SECTION_ICON[section.key] ?? Handshake;
        return (
          <section key={section.key} aria-labelledby={`section-${section.key}`} className="space-y-4">
            <h2 id={`section-${section.key}`} className="flex items-center gap-3 text-lg font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                <Icon className="size-[18px]" />
              </span>
              {section.title}
              <span aria-hidden="true" className="h-px flex-1 bg-gradient-to-r from-border to-transparent" />
            </h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {section.metrics.map((metric) => {
                const zero = metric.value === 0;
                return (
                  <Link key={metric.label} href={metric.href}
                    className="group relative overflow-hidden rounded-2xl border border-border bg-surface p-5 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-lg hover:shadow-gold/10">
                    <span aria-hidden="true" className="absolute inset-x-0 top-0 h-0.5 origin-left scale-x-0 bg-gold transition-transform duration-500 group-hover:scale-x-100" />
                    <div className="flex items-start justify-between gap-3">
                      <p className="text-sm text-muted">{metric.label}</p>
                      <ArrowUpRight aria-hidden="true" className="size-4 shrink-0 text-muted transition duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-text" />
                    </div>
                    <p className={`mt-3 font-display text-3xl font-semibold tracking-tight tabular-nums ${zero ? "text-muted" : ""}`}>
                      {metric.value.toLocaleString("en-IN")}
                    </p>
                  </Link>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
