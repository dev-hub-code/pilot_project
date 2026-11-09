import type { Metadata } from "next";
import Link from "next/link";
import { ColumnChart, type ColumnDatum, type ColumnSeries } from "@/components/charts/column-chart";
import {
  Activity, ArrowUpRight, BarChart3, CheckCircle2, Clock, Download, Eye, FileCheck, Globe, IdCard, KeyRound, Landmark, Lock, LogIn,
  type LucideIcon, MonitorSmartphone, Paperclip, RotateCcwKey, ScrollText, Shield, ShieldAlert, ShieldCheck, Unlock, UserCog, UserX,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { AuditList } from "@/features/security/audit-list";
import { PERIODS, personLabel } from "@/features/security/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { SecurityOverview, UserRef } from "@/types/security";
import { formatDateTime } from "@/utils/format";
import { widthClass } from "@/utils/width-class";
import { EmailText } from "@/components/ui/email-text";

export const metadata: Metadata = { title: "Security" };

const ACTIVITY_SERIES: readonly ColumnSeries[] = [
  { key: "signIns", label: "Successful sign-ins", fill: "fill-chart-2", swatch: "bg-chart-2" },
  { key: "failed", label: "Failed sign-ins", fill: "fill-rose-500", swatch: "bg-rose-500" },
];

const hourLabel = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: "UTC" });
const dayLabel = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });
const longLabel = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: "UTC" });

function activityColumns(overview: SecurityOverview): ColumnDatum[] {
  return overview.activity.map((a) => {
    const start = new Date(`${a.start}Z`);
    const hourly = overview.bucket === "HOUR";
    return {
      label: hourly ? hourLabel.format(start) : dayLabel.format(start),
      title: hourly ? `${longLabel.format(start)}, ${hourLabel.format(start)} UTC` : `${longLabel.format(start)} (UTC)`,
      values: { signIns: a.signIns, failed: a.failedSignIns },
    };
  });
}

export default async function SecurityPage({ searchParams }: PageProps<"/platform/security">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.AUDIT_VIEW)) {
    return <EmptyState title="Not available" description="The security dashboard needs the AUDIT_VIEW permission." />;
  }
  const params = await searchParams;
  const requested = Number(params.hours);
  const hours = PERIODS.some((p) => p.hours === requested) ? requested : 168;
  const overview = await authFetch<SecurityOverview>(`/api/v1/admin/security/overview?hours=${hours}`);
  const t = overview.totals;
  const since = overview.from.slice(0, 10);
  const log = (filters: Record<string, string>) => `/platform/audit?${new URLSearchParams({ from: since, ...filters })}`;

  const tiles: Tile[] = [
    { icon: LogIn, label: "Sign-ins", value: t.signIns, note: "Successful", href: log({ action: "LOGIN" }) },
    { icon: KeyRound, label: "Failed sign-ins", value: t.failedSignIns, note: "Wrong password on an existing account",
      href: log({ action: "LOGIN_FAILED" }), alert: t.failedSignIns > 0 },
    { icon: Lock, label: "Accounts locked now", value: overview.lockedAccounts.length, note: `${t.lockouts} lockouts in the period`,
      href: "#locked", alert: overview.lockedAccounts.length > 0 },
    { icon: ShieldAlert, label: "Stolen-token alerts", value: t.stolenTokenDetections, note: "Sessions ended after token reuse",
      href: log({ action: "TOKEN_REFRESH_REUSE_DETECTED" }), alert: t.stolenTokenDetections > 0 },
    { icon: MonitorSmartphone, label: "Active sessions", value: t.activeSessions, note: `${t.signedInUsers} people signed in now`, href: null },
    { icon: RotateCcwKey, label: "Password resets", value: t.passwordResets, note: `${t.passwordChanges} password${t.passwordChanges === 1 ? "" : "s"} changed`,
      href: log({ action: "PASSWORD_RESET" }) },
    { icon: Eye, label: "Personal data opened", value: t.sensitiveAccess, note: "KYC, bank proofs, attachments, reports",
      href: log({ category: "SENSITIVE_DATA" }) },
    { icon: UserCog, label: "Access & settings changes", value: t.privilegedChanges, note: "Roles, staff, suspensions, money settings",
      href: "#changes" },
  ];
  const concerns = [
    overview.lockedAccounts.length > 0 && `${overview.lockedAccounts.length} account${overview.lockedAccounts.length === 1 ? "" : "s"} locked`,
    t.stolenTokenDetections > 0 && `${t.stolenTokenDetections} stolen-token alert${t.stolenTokenDetections === 1 ? "" : "s"}`,
    t.failedSignIns > 0 && `${t.failedSignIns} failed sign-in${t.failedSignIns === 1 ? "" : "s"}`,
  ].filter((c): c is string => Boolean(c));
  const serious = overview.lockedAccounts.length > 0 || t.stolenTokenDetections > 0;
  const maxAccount = Math.max(1, ...overview.failedByAccount.map((r) => r.attempts));
  const maxIp = Math.max(1, ...overview.failedByIp.map((r) => r.attempts));
  const ACCESS_KINDS = [
    { key: "kycDocuments", label: "KYC documents", icon: IdCard },
    { key: "kycSubmissions", label: "KYC submissions", icon: FileCheck },
    { key: "bankProofs", label: "Bank proofs", icon: Landmark },
    { key: "ticketAttachments", label: "Attachments", icon: Paperclip },
    { key: "reports", label: "Reports", icon: BarChart3 },
    { key: "auditExports", label: "Log exports", icon: Download },
  ] as const;

  return (
    <div className="space-y-6">
      <section className="relative isolate overflow-hidden rounded-3xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 sm:p-8">
        <div aria-hidden="true" className={`absolute -top-24 -right-20 -z-10 size-72 rounded-full blur-3xl ${serious ? "bg-rose-500/25" : "bg-gold/25"}`} />
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-4">
            <span aria-hidden="true" className={`flex size-12 shrink-0 items-center justify-center rounded-2xl ${serious
              ? "bg-rose-500 text-white" : concerns.length > 0 ? "bg-amber-400 text-ink" : "bg-emerald-500 text-white"}`}>
              {serious ? <ShieldAlert className="size-6" /> : concerns.length > 0 ? <Shield className="size-6" /> : <ShieldCheck className="size-6" />}
            </span>
            <div className="min-w-0">
              <h1 className="text-2xl font-semibold tracking-tight">Security</h1>
              <p className="text-sm text-on-ink/70">
                {concerns.length === 0 ? "All quiet: no failed sign-ins, lockouts or stolen-token alerts in this period." : concerns.join(" · ")}
              </p>
            </div>
          </div>
          <Link href="/platform/audit" className="inline-flex items-center gap-1.5 rounded-xl bg-white/10 px-3.5 py-2 text-sm font-medium transition-colors hover:bg-white/15">
            <ScrollText aria-hidden="true" className="size-4" /> Open audit log
          </Link>
        </div>
        <div className="mt-6 flex flex-wrap items-center gap-3">
          <nav aria-label="Period" className="grid w-full grid-cols-4 gap-1 rounded-xl bg-white/10 p-1 text-sm sm:inline-grid sm:w-auto">
            {PERIODS.map((p) => (
              <Link key={p.hours} href={`/platform/security?hours=${p.hours}`} aria-current={p.hours === hours ? "page" : undefined}
                className={`rounded-lg px-2 py-1.5 text-center font-medium whitespace-nowrap transition-colors sm:px-3 ${p.hours === hours
                  ? "bg-gold text-on-gold shadow-sm" : "text-on-ink/70 hover:bg-white/10 hover:text-on-ink"}`}>
                {p.label}
              </Link>
            ))}
          </nav>
          <span className="inline-flex items-center gap-1.5 text-xs text-on-ink/60">
            <Clock aria-hidden="true" className="size-3.5" /> Since {formatDateTime(overview.from)}
          </span>
        </div>
      </section>

      <ul aria-label="Key figures" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {tiles.map((tile) => {
          const Icon = tile.icon;
          const body = (
            <>
              <div className="flex items-start justify-between gap-2">
                <span aria-hidden="true" className={`flex size-9 items-center justify-center rounded-xl ${tile.alert
                  ? "bg-rose-500/10 text-rose-600 dark:text-rose-400" : "bg-gold/10 text-gold-text"}`}>
                  <Icon className="size-[18px]" />
                </span>
                {tile.href && <ArrowUpRight aria-hidden="true" className="size-4 text-muted transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />}
              </div>
              <p className={`mt-2 text-xl font-semibold sm:mt-3 sm:text-2xl tracking-tight tabular-nums ${tile.alert ? "text-rose-600 dark:text-rose-400" : ""}`}>{tile.value}</p>
              <p className="text-xs font-medium sm:text-sm">{tile.label}</p>
              <p className="hidden text-xs text-muted sm:block">{tile.note}</p>
            </>
          );
          const box = `group block h-full rounded-2xl border bg-surface p-3.5 shadow-sm sm:p-4 ${tile.alert ? "border-rose-500/30" : "border-border"}`;
          return (
            <li key={tile.label}>
              {tile.href
                ? <Link href={tile.href} className={`${box} transition duration-200 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-md`}>{body}</Link>
                : <div className={box}>{body}</div>}
            </li>
          );
        })}
      </ul>

      <Section icon={Activity} title="Sign-in activity" description={overview.bucket === "HOUR" ? "Each hour (UTC)" : "Each day (UTC)"}>
        <div className="min-w-0 p-5">
          <ColumnChart series={ACTIVITY_SERIES} data={activityColumns(overview)} label="Sign-ins and failed sign-ins over time" />
        </div>
      </Section>

      <div className="grid gap-6 lg:grid-cols-2">
        <Section icon={UserX} title="Failed sign-ins by account" description="Many on one account suggests password guessing">
          {overview.failedByAccount.length === 0 ? <Quiet text="No failed sign-ins" /> : (
            <ul className="divide-y divide-border">
              {overview.failedByAccount.map((row) => (
                <AttemptRow key={row.account.id} attempts={row.attempts} max={maxAccount} latest={row.latest}
                  href={`/platform/audit?${new URLSearchParams({ action: "LOGIN_FAILED", entityType: "USER", entityId: row.account.id, from: since })}`}>
                  <Person person={row.account} />
                </AttemptRow>
              ))}
            </ul>
          )}
        </Section>
        <Section icon={Globe} title="Failed sign-ins by IP address" description="Many from one address suggests one source trying many accounts">
          {overview.failedByIp.length === 0 ? <Quiet text="No failed sign-ins" /> : (
            <ul className="divide-y divide-border">
              {overview.failedByIp.map((row) => (
                <AttemptRow key={row.ipAddress} attempts={row.attempts} max={maxIp} latest={row.latest}
                  href={log({ action: "LOGIN_FAILED", ip: row.ipAddress })}>
                  <span className="font-mono text-xs">{row.ipAddress}</span>
                </AttemptRow>
              ))}
            </ul>
          )}
        </Section>
      </div>

      <Section id="locked" icon={Lock} title="Locked accounts" count={overview.lockedAccounts.length}
        description="Locked after repeated failed sign-ins; they unlock by themselves at the time shown" alert={overview.lockedAccounts.length > 0}>
        {overview.lockedAccounts.length === 0 ? <Quiet text="No account is locked" /> : (
          <ul className="divide-y divide-border">
            {overview.lockedAccounts.map((row) => (
              <li key={row.account.id} className="grid gap-2 px-5 py-3 text-sm sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                <div className="flex min-w-0 items-center gap-3">
                  <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-rose-500/10 text-rose-600 dark:text-rose-400">
                    <Lock className="size-4" />
                  </span>
                  <div className="min-w-0 truncate"><Person person={row.account} /></div>
                </div>
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className="rounded-full bg-rose-500/10 px-2.5 py-1 font-medium text-rose-700 tabular-nums dark:text-rose-300">{row.failedAttempts} failed</span>
                  <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 text-muted ring-1 ring-border">
                    <Unlock aria-hidden="true" className="size-3.5" /> Unlocks {formatDateTime(row.lockedUntil)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section icon={Eye} title="Staff access to personal data" count={overview.sensitiveAccess.length}
        description="Who opened identity documents, bank proofs and attachments, or took data out">
        {overview.sensitiveAccess.length === 0 ? <Quiet text="No access in this period" /> : (
          <ul className="divide-y divide-border">
            {overview.sensitiveAccess.map((row) => (
              <li key={row.staff.id} className="grid gap-3 px-5 py-4 lg:grid-cols-[minmax(0,16rem)_minmax(0,1fr)] lg:items-center">
                <div className="min-w-0 text-sm">
                  <Link href={log({ category: "SENSITIVE_DATA", actor: row.staff.id })} className="block truncate font-medium hover:text-gold-text"
                    title={personLabel(row.staff)}>{row.staff.email ? <EmailText>{row.staff.email}</EmailText> : "Deleted account"}</Link>
                  <p className="text-xs text-muted">Latest {formatDateTime(row.latest)}</p>
                </div>
                <ul className="flex flex-wrap gap-1.5 text-xs">
                  {ACCESS_KINDS.map(({ key, label, icon: Icon }) => {
                    const n = row[key];
                    return (
                      <li key={key} className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 ring-1 ${n > 0
                        ? "bg-gold/10 text-gold-text ring-gold/20" : "bg-background text-muted/70 ring-border"}`}>
                        <Icon aria-hidden="true" className="size-3.5" /> {label} <span className="font-semibold tabular-nums">{n}</span>
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <div id="changes" className="grid scroll-mt-6 gap-6">
        <Section icon={UserCog} title="Access & settings changes" description="Roles, staff accounts, suspensions and money settings; latest 10">
          {overview.privilegedChanges.length === 0 ? <Quiet text="No changes in this period" /> : (
            <div className="p-4"><AuditList entries={overview.privilegedChanges} showIp={false} /></div>
          )}
        </Section>
        <Section icon={ShieldAlert} title="Security events" description="Lockouts, stolen-token alerts and password changes; latest 10">
          {overview.securityEvents.length === 0 ? <Quiet text="No security events in this period" /> : (
            <div className="p-4"><AuditList entries={overview.securityEvents} showIp={false} /></div>
          )}
        </Section>
      </div>
    </div>
  );
}

type Tile = { icon: LucideIcon; label: string; value: number; note: string; href: string | null; alert?: boolean };

function Section({ id, icon: Icon, title, description, count, alert, children }: {
  id?: string; icon: LucideIcon; title: string; description?: string; count?: number; alert?: boolean; children: React.ReactNode;
}) {
  return (
    <section id={id} className={`scroll-mt-6 overflow-hidden rounded-2xl border bg-surface shadow-sm ${alert ? "border-rose-500/30" : "border-border"}`}>
      <div className="flex items-start gap-3 border-b border-border bg-background/40 px-5 py-4">
        <span aria-hidden="true" className={`flex size-9 shrink-0 items-center justify-center rounded-xl shadow-sm ${alert
          ? "bg-rose-500 text-white" : "bg-ink text-on-ink dark:bg-gold dark:text-on-gold"}`}>
          <Icon className="size-[18px]" />
        </span>
        <div className="min-w-0">
          <h2 className="font-semibold tracking-tight">{title}{count !== undefined && <span className="ml-2 text-sm font-normal text-muted">{count}</span>}</h2>
          {description && <p className="text-xs text-muted">{description}</p>}
        </div>
      </div>
      {children}
    </section>
  );
}

function Quiet({ text }: { text: string }) {
  return (
    <p className="flex items-center gap-2 px-5 py-6 text-sm text-muted">
      <CheckCircle2 aria-hidden="true" className="size-4 text-emerald-600 dark:text-emerald-400" /> {text}
    </p>
  );
}

function AttemptRow({ attempts, max, latest, href, children }: {
  attempts: number; max: number; latest: string; href: string; children: React.ReactNode;
}) {
  return (
    <li className="space-y-2 px-5 py-3 text-sm">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0 truncate">{children}</div>
        <Link href={href} className="shrink-0 rounded-full bg-rose-500/10 px-2.5 py-0.5 text-xs font-semibold text-rose-700 tabular-nums hover:bg-rose-500/20 dark:text-rose-300">
          {attempts} {attempts === 1 ? "attempt" : "attempts"}
        </Link>
      </div>
      <div className="flex items-center gap-3">
        <div aria-hidden="true" className="h-1.5 flex-1 overflow-hidden rounded-full bg-background ring-1 ring-border">
          <div className={`h-full rounded-full bg-rose-500/70 ${widthClass((attempts / max) * 100)}`} />
        </div>
        <span className="shrink-0 text-[11px] text-muted">Latest {formatDateTime(latest)}</span>
      </div>
    </li>
  );
}

function Person({ person }: { person: UserRef }) {
  return (
    <Link href={`/platform/users/${person.id}`} className="font-medium hover:text-gold-text" title={personLabel(person)}>
      {person.email ? <EmailText>{person.email}</EmailText> : "Deleted account"}
    </Link>
  );
}
