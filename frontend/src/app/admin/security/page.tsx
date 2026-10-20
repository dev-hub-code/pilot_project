import type { Metadata } from "next";
import Link from "next/link";
import { ColumnChart, type ColumnDatum, type ColumnSeries } from "@/components/charts/column-chart";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { AuditTable } from "@/features/security/audit-table";
import { PERIODS, personLabel } from "@/features/security/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { SecurityOverview, UserRef } from "@/types/security";
import { formatDateTime } from "@/utils/format";
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

export default async function SecurityPage({ searchParams }: PageProps<"/admin/security">) {
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
  const log = (filters: Record<string, string>) => `/admin/audit?${new URLSearchParams({ from: since, ...filters })}`;

  const tiles = [
    { label: "Sign-ins", value: t.signIns, note: "Successful", href: log({ action: "LOGIN" }) },
    { label: "Failed sign-ins", value: t.failedSignIns, note: "Wrong password on an existing account",
      href: log({ action: "LOGIN_FAILED" }), alert: t.failedSignIns > 0 },
    { label: "Accounts locked now", value: overview.lockedAccounts.length, note: `${t.lockouts} lockouts in the period`,
      href: "#locked", alert: overview.lockedAccounts.length > 0 },
    { label: "Stolen-token alerts", value: t.stolenTokenDetections, note: "Sessions ended after token reuse",
      href: log({ action: "TOKEN_REFRESH_REUSE_DETECTED" }), alert: t.stolenTokenDetections > 0 },
    { label: "Active sessions", value: t.activeSessions, note: `${t.signedInUsers} people signed in now`, href: null },
    { label: "Password resets", value: t.passwordResets, note: `${t.passwordChanges} passwords changed`,
      href: log({ action: "PASSWORD_RESET" }) },
    { label: "Personal data opened", value: t.sensitiveAccess, note: "KYC, bank proofs, attachments, reports",
      href: log({ category: "SENSITIVE_DATA" }) },
    { label: "Access & settings changes", value: t.privilegedChanges, note: "Roles, staff, suspensions, money settings",
      href: "#changes" },
  ];

  return (
    <div className="space-y-8">
      <PageHeader title="Security" description="Sign-in activity, locked accounts and staff access to personal data, from the audit log."
        actions={<LinkButton href="/admin/audit" variant="secondary">Open audit log</LinkButton>} />

      <nav aria-label="Period" className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-muted">Last</span>
        {PERIODS.map((p) => (
          <Link key={p.hours} href={`/admin/security?hours=${p.hours}`} aria-current={p.hours === hours ? "page" : undefined}
            className={`rounded-full border px-4 py-1.5 ${p.hours === hours
              ? "border-foreground bg-foreground text-surface" : "border-border hover:border-foreground/60"}`}>
            {p.label}
          </Link>
        ))}
        <span className="text-xs text-muted">Since {formatDateTime(overview.from)}</span>
      </nav>

      <section className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-4" aria-label="Key figures">
        {tiles.map((tile) => {
          const body = (
            <>
              <p className="text-xs uppercase tracking-[0.1em] text-muted">{tile.label}</p>
              <p className={`font-display text-2xl font-semibold tabular-nums ${tile.alert ? "text-rose-600 dark:text-rose-400" : ""}`}>
                {tile.value}
              </p>
              <p className="text-xs text-muted">{tile.note}</p>
            </>
          );
          return tile.href ? (
            <Link key={tile.label} href={tile.href} className="space-y-2 bg-surface p-5 hover:bg-background">{body}</Link>
          ) : (
            <div key={tile.label} className="space-y-2 bg-surface p-5">{body}</div>
          );
        })}
      </section>

      <Card title="Sign-in activity" description={overview.bucket === "HOUR" ? "Each hour (UTC)" : "Each day (UTC)"} className="min-w-0">
        <ColumnChart series={ACTIVITY_SERIES} data={activityColumns(overview)} label="Sign-ins and failed sign-ins over time" />
      </Card>

      <section className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0" aria-label="Failed sign-ins">
        <Card title="Failed sign-ins by account" description="Many on one account suggests password guessing">
          {overview.failedByAccount.length === 0 ? <EmptyState title="No failed sign-ins" /> : (
            <DataTable columns={["Account", "Attempts", "Latest"]}>
              {overview.failedByAccount.map((row) => (
                <tr key={row.account.id}>
                  <Cell className="max-w-64 truncate"><Person person={row.account} /></Cell>
                  <Cell className="tabular-nums">
                    <Link href={`/admin/audit?${new URLSearchParams({ action: "LOGIN_FAILED", entityType: "USER", entityId: row.account.id, from: since })}`}
                      className="text-brand hover:underline">{row.attempts}</Link>
                  </Cell>
                  <Cell className="text-muted">{formatDateTime(row.latest)}</Cell>
                </tr>
              ))}
            </DataTable>
          )}
        </Card>
        <Card title="Failed sign-ins by IP address" description="Many from one address suggests one source trying many accounts">
          {overview.failedByIp.length === 0 ? <EmptyState title="No failed sign-ins" /> : (
            <DataTable columns={["IP address", "Attempts", "Latest"]}>
              {overview.failedByIp.map((row) => (
                <tr key={row.ipAddress}>
                  <Cell className="font-mono text-xs">{row.ipAddress}</Cell>
                  <Cell className="tabular-nums">
                    <Link href={log({ action: "LOGIN_FAILED", ip: row.ipAddress })} className="text-brand hover:underline">{row.attempts}</Link>
                  </Cell>
                  <Cell className="text-muted">{formatDateTime(row.latest)}</Cell>
                </tr>
              ))}
            </DataTable>
          )}
        </Card>
      </section>

      <section id="locked" className="scroll-mt-6">
        <Card title="Locked accounts" description="Locked after repeated failed sign-ins; they unlock by themselves at the time shown">
          {overview.lockedAccounts.length === 0 ? <EmptyState title="No account is locked" /> : (
            <DataTable columns={["Account", "Failed attempts", "Locked until"]}>
              {overview.lockedAccounts.map((row) => (
                <tr key={row.account.id}>
                  <Cell><Person person={row.account} /></Cell>
                  <Cell className="tabular-nums">{row.failedAttempts}</Cell>
                  <Cell className="text-muted">{formatDateTime(row.lockedUntil)}</Cell>
                </tr>
              ))}
            </DataTable>
          )}
        </Card>
      </section>

      <Card title="Staff access to personal data" description="Who opened identity documents, bank proofs and attachments, or took data out">
        {overview.sensitiveAccess.length === 0 ? <EmptyState title="No access in this period" /> : (
          <DataTable columns={["Staff member", "KYC documents", "KYC submissions", "Bank proofs", "Attachments", "Reports", "Log exports", "Latest"]}>
            {overview.sensitiveAccess.map((row) => (
              <tr key={row.staff.id}>
                <Cell className="max-w-64 truncate">
                  <Link href={log({ category: "SENSITIVE_DATA", actor: row.staff.id })} className="text-brand hover:underline"
                    title={personLabel(row.staff)}>{row.staff.email ? <EmailText>{row.staff.email}</EmailText> : "Deleted account"}</Link>
                </Cell>
                {[row.kycDocuments, row.kycSubmissions, row.bankProofs, row.ticketAttachments, row.reports, row.auditExports].map((n, i) => (
                  <Cell key={i} className={`tabular-nums ${n === 0 ? "text-muted" : ""}`}>{n}</Cell>
                ))}
                <Cell className="text-muted">{formatDateTime(row.latest)}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <section id="changes" className="grid scroll-mt-6 gap-6 xl:grid-cols-2 [&>*]:min-w-0">
        <Card title="Access & settings changes" description="Roles, staff accounts, suspensions and money settings; latest 10">
          {overview.privilegedChanges.length === 0 ? <EmptyState title="No changes in this period" /> : (
            <AuditTable entries={overview.privilegedChanges} showIp={false} />
          )}
        </Card>
        <Card title="Security events" description="Lockouts, stolen-token alerts and password changes; latest 10">
          {overview.securityEvents.length === 0 ? <EmptyState title="No security events in this period" /> : (
            <AuditTable entries={overview.securityEvents} showIp={false} />
          )}
        </Card>
      </section>
    </div>
  );
}

function Person({ person }: { person: UserRef }) {
  return (
    <Link href={`/admin/users/${person.id}`} className="text-brand hover:underline" title={personLabel(person)}>
      {person.email ? <EmailText>{person.email}</EmailText> : "Deleted account"}
    </Link>
  );
}
