import type { Metadata } from "next";
import Link from "next/link";
import {
  ChevronDown, Download, Layers, ListChecks, Lock, ScrollText, Search, SlidersHorizontal, X,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { TextField } from "@/components/ui/text-field";
import { AuditList, CATEGORY_ICON } from "@/features/security/audit-list";
import { ACTIONS_BY_CATEGORY, CATEGORY_LABEL, actionLabel } from "@/features/security/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AuditCategory, AuditLogEntry } from "@/types/security";

export const metadata: Metadata = { title: "Audit log" };

const CATEGORIES = Object.keys(CATEGORY_LABEL) as AuditCategory[];
const DATE = /^\d{4}-\d{2}-\d{2}$/;

function text(value: unknown, max: number): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim().slice(0, max) : undefined;
}

export default async function AuditLogPage({ searchParams }: PageProps<"/admin/audit">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.AUDIT_VIEW)) {
    return <EmptyState title="Not available" description="The audit log needs the AUDIT_VIEW permission." />;
  }
  const params = await searchParams;
  const category = CATEGORIES.find((c) => c === params.category);
  const actions = category ? ACTIONS_BY_CATEGORY[category] : CATEGORIES.flatMap((c) => ACTIONS_BY_CATEGORY[c]);
  const filters = {
    category,
    action: actions.find((a) => a === params.action),
    actor: text(params.actor, 320),
    entityType: text(params.entityType, 64),
    entityId: text(params.entityId, 64),
    ip: text(params.ip, 45),
    from: typeof params.from === "string" && DATE.test(params.from) ? params.from : undefined,
    to: typeof params.to === "string" && DATE.test(params.to) ? params.to : undefined,
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const entries = await authFetch<PageResponse<AuditLogEntry>>(`/api/v1/admin/audit-logs?${query}&page=${page}&size=50`);
  const filtered = query.size > 0;

  const pill = (active: boolean) => `inline-flex items-center gap-1.5 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${active
    ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold" : "text-muted hover:bg-background hover:text-foreground"}`;
  const without = (...keys: (keyof typeof filters)[]) => {
    const next = new URLSearchParams(query);
    for (const key of keys) next.delete(key);
    const qs = next.toString();
    return qs ? `/admin/audit?${qs}` : "/admin/audit";
  };
  const chips: { label: string; href: string }[] = [
    filters.action && { label: `Action: ${actionLabel(filters.action)}`, href: without("action") },
    filters.actor && { label: `By: ${filters.actor}`, href: without("actor") },
    filters.ip && { label: `IP: ${filters.ip}`, href: without("ip") },
    filters.entityType && { label: `Record type: ${filters.entityType}`, href: without("entityType") },
    filters.entityId && { label: `Record: ${filters.entityId.length > 12 ? `${filters.entityId.slice(0, 8)}…` : filters.entityId}`, href: without("entityId") },
    filters.from && { label: `From ${filters.from}`, href: without("from") },
    filters.to && { label: `To ${filters.to}`, href: without("to") },
  ].filter((c): c is { label: string; href: string } => Boolean(c));
  const moreOpen = Boolean(filters.action || filters.ip || filters.entityType || filters.entityId || filters.from || filters.to);

  return (
    <div className="space-y-6">
      <section className="relative isolate overflow-hidden rounded-3xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 sm:p-8">
        <div aria-hidden="true" className="absolute -top-24 -right-20 -z-10 size-72 rounded-full bg-gold/25 blur-3xl" />
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-4">
            <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gold text-on-gold">
              <ScrollText className="size-6" />
            </span>
            <div className="min-w-0">
              <h1 className="text-2xl font-semibold tracking-tight">Audit log</h1>
              <p className="text-sm text-on-ink/70">Every sign-in, permission change, document opened and money movement, newest first.</p>
            </div>
          </div>
          {/* A plain link: a prefetch would run (and audit) an export nobody asked for. */}
          <a href={`/admin/audit/export?${query}`}
            className="inline-flex items-center gap-1.5 rounded-xl bg-white/10 px-3.5 py-2 text-sm font-medium transition-colors hover:bg-white/15">
            <Download aria-hidden="true" className="size-4" /> Download CSV{filtered ? " (filtered)" : ""}
          </a>
        </div>
        <ul className="mt-5 flex flex-wrap gap-2 text-xs">
          <li className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
            <Lock aria-hidden="true" className="size-3.5 text-gold-on-ink" /> Entries cannot be changed or deleted
          </li>
          <li className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
            <ListChecks aria-hidden="true" className="size-3.5 text-gold-on-ink" /> {entries.totalElements.toLocaleString("en-IN")} {filtered ? "matching" : "entries"}
          </li>
        </ul>
      </section>

      <nav aria-label="Category" className="flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
        <Link href={without("category", "action")} aria-current={!category ? "page" : undefined} className={pill(!category)}>
          <Layers aria-hidden="true" className="size-4" /> All
        </Link>
        {CATEGORIES.map((c) => {
          const Icon = CATEGORY_ICON[c];
          const next = new URLSearchParams(query);
          next.set("category", c);
          next.delete("action");
          return (
            <Link key={c} href={`/admin/audit?${next}`} aria-current={c === category ? "page" : undefined} className={pill(c === category)}>
              <Icon aria-hidden="true" className="size-4" /> {CATEGORY_LABEL[c]}
            </Link>
          );
        })}
      </nav>

      <form role="search" className="space-y-3">
        {category && <input type="hidden" name="category" value={category} />}
        <div className="flex items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold">
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="actor" type="search" defaultValue={filters.actor} placeholder="Who did it: email or user id" aria-label="By (email or user id)"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none" />
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
        </div>
        <details open={moreOpen} className="group rounded-xl border border-border bg-surface shadow-sm">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-2.5 text-sm font-medium marker:hidden">
            <SlidersHorizontal aria-hidden="true" className="size-4 text-gold-text" /> More filters
            <ChevronDown aria-hidden="true" className="ml-auto size-4 text-muted transition-transform group-open:rotate-180" />
          </summary>
          <div className="grid items-end gap-3 border-t border-border p-4 sm:grid-cols-2 lg:grid-cols-3">
            <SelectField label="Action" name="action" placeholder="Any" defaultValue={filters.action ?? ""}
              options={[...actions].sort((a, b) => actionLabel(a).localeCompare(actionLabel(b))).map((a) => ({ value: a, label: actionLabel(a) }))} />
            <TextField label="IP address" name="ip" type="search" placeholder="203.0.113.7" defaultValue={filters.ip} />
            <TextField label="Record type" name="entityType" type="search" placeholder="USER, ORDER, KYC_SUBMISSION…" defaultValue={filters.entityType} />
            <TextField label="Record id" name="entityId" type="search" placeholder="The record's id" defaultValue={filters.entityId} />
            <TextField label="From (UTC)" name="from" type="date" defaultValue={filters.from} />
            <TextField label="To (UTC)" name="to" type="date" defaultValue={filters.to} />
            <div className="flex gap-2 sm:col-span-2 lg:col-span-3">
              <button type="submit" className="h-10 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Apply filters</button>
              {filtered && <Link href="/admin/audit" className="inline-flex h-10 items-center rounded-lg px-4 text-sm font-medium text-muted hover:bg-background hover:text-foreground">Clear all</Link>}
            </div>
          </div>
        </details>
      </form>

      {chips.length > 0 && (
        <ul aria-label="Active filters" className="flex flex-wrap gap-2 text-xs">
          {chips.map((c) => (
            <li key={c.label}>
              <Link href={c.href} aria-label={`Remove ${c.label}`}
                className="inline-flex items-center gap-1.5 rounded-full bg-gold/10 px-3 py-1.5 font-medium text-gold-text ring-1 ring-gold/20 hover:bg-gold/20">
                {c.label} <X aria-hidden="true" className="size-3.5" />
              </Link>
            </li>
          ))}
        </ul>
      )}

      {entries.content.length === 0 ? (
        <EmptyState title={filtered ? "No entries match these filters" : "The audit log is empty"} />
      ) : (
        <AuditList entries={entries.content} />
      )}
      <Pagination page={entries} basePath="/admin/audit" params={filters} />
    </div>
  );
}
