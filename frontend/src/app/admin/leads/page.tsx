import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { STAGES, STAGE_LABEL, STAGE_TONE } from "@/features/leads/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Lead, LeadStage, PipelineStage } from "@/types/lead";
import { formatDate, formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { EmailText } from "@/components/ui/email-text";
import { formatPhone } from "@/lib/phone";
import {
  BellRing, CalendarClock, CheckCircle2, Contact, FileSignature, Globe, Handshake, type LucideIcon, Mail, Phone, Search, Sparkles,
  UserPlus, UserRound, Users, XCircle,
} from "lucide-react";

/** Each stage's icon and colour, along the pipeline from first contact to won or lost. */
const STAGE_STYLE: Record<LeadStage, { icon: LucideIcon; tile: string }> = {
  NEW: { icon: Sparkles, tile: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300" },
  CONTACTED: { icon: Phone, tile: "bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300" },
  QUALIFIED: { icon: Handshake, tile: "bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300" },
  PROPOSAL: { icon: FileSignature, tile: "bg-gold/10 text-gold-text" },
  WON: { icon: CheckCircle2, tile: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300" },
  LOST: { icon: XCircle, tile: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300" },
};

export const metadata: Metadata = { title: "Leads" };

export default async function LeadsPage({ searchParams }: PageProps<"/admin/leads">) {
  const denied = await deniedUnlessInMenu("/admin/leads");
  if (denied) return denied;
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const params = await searchParams;
  const manager = can(Permission.LEAD_ASSIGN);
  const owners = ["all", "me", "unassigned"] as const;
  const owner = owners.find((o) => o === params.owner) ?? (manager ? "all" : "me");
  const stage = STAGES.find((s) => s === params.stage);
  const due = params.due === "true";
  const q = typeof params.q === "string" ? params.q.slice(0, 100) : "";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (owner !== "all") query.set("owner", owner);
  if (stage) query.set("stage", stage);
  if (due) query.set("due", "true");
  if (q) query.set("q", q);
  const [pipeline, leads] = await Promise.all([
    authFetch<PipelineStage[]>(`/api/v1/admin/leads/pipeline${owner !== "all" ? `?owner=${owner}` : ""}`),
    authFetch<PageResponse<Lead>>(`/api/v1/admin/leads?${query}`),
  ]);
  const link = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams();
    const merged = { owner, stage, due: due ? "true" : undefined, q: q || undefined, ...patch };
    for (const [k, v] of Object.entries(merged)) if (v) next.set(k, v);
    return `/admin/leads?${next}`;
  };

  const today = new Date().toISOString().slice(0, 10);
  const ownerLabel = (o: (typeof owners)[number]) => (o === "all" ? (manager ? "Everyone" : "All I can see") : o === "me" ? "Mine" : "Unassigned");
  const ownerIcon = (o: (typeof owners)[number]) => (o === "all" ? Users : o === "me" ? UserRound : Contact);

  return (
    <div className="space-y-6">
      <PageHeader title="Leads"
        description={`${manager ? "The whole sales pipeline." : "Your leads and unassigned enquiries."} ${leads.totalElements.toLocaleString("en-IN")} ${leads.totalElements === 1 ? "lead" : "leads"}${stage || due || q ? " match" : ""}.`}
        actions={can(Permission.LEAD_CREATE)
          ? <LinkButton href="/admin/leads/new" className="gap-2"><UserPlus aria-hidden="true" className="size-4" /> New lead</LinkButton> : undefined} />

      <section aria-label="Pipeline" className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {pipeline.map((p) => {
          const st = STAGE_STYLE[p.stage as LeadStage] ?? STAGE_STYLE.NEW;
          const active = p.stage === stage;
          return (
            <Link key={p.stage} href={link({ stage: active ? undefined : p.stage })} aria-current={active ? "page" : undefined}
              className={`group relative min-w-0 overflow-hidden rounded-2xl border bg-surface p-4 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-gold/10 ${active
                ? "border-gold ring-2 ring-gold/30" : "border-border hover:border-gold/40"}`}>
              <span aria-hidden="true" className={`absolute inset-x-0 top-0 h-0.5 origin-left bg-gold transition-transform duration-500 ${active ? "scale-x-100" : "scale-x-0 group-hover:scale-x-100"}`} />
              <div className="flex items-center justify-between gap-2">
                <p className="truncate text-sm text-muted">{STAGE_LABEL[p.stage as LeadStage] ?? p.stage}</p>
                <span aria-hidden="true" className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${st.tile}`}>
                  <st.icon className="size-4" />
                </span>
              </div>
              <p className={`mt-2 font-display text-3xl font-semibold tracking-tight tabular-nums ${p.leads === 0 ? "text-muted" : ""}`}>{p.leads.toLocaleString("en-IN")}</p>
              <p className="truncate text-xs text-muted">{p.value.map((m) => formatMoney(m, { compact: true })).join(" · ") || "—"}</p>
            </Link>
          );
        })}
      </section>

      <div className="flex flex-wrap items-center gap-3">
        <nav aria-label="Owner" className="inline-flex max-w-full flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {owners.map((o) => {
            const Icon = ownerIcon(o);
            return (
              <Link key={o} href={link({ owner: o })} aria-current={o === owner ? "page" : undefined}
                className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${o === owner
                  ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                  : "text-muted hover:bg-background hover:text-foreground"}`}>
                <Icon aria-hidden="true" className="size-4" /> {ownerLabel(o)}
              </Link>
            );
          })}
        </nav>
        <Link href={link({ due: due ? undefined : "true" })} aria-current={due ? "page" : undefined}
          className={`inline-flex min-h-11 items-center gap-1.5 rounded-xl border px-4 text-sm font-medium shadow-sm transition-colors ${due
            ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300"
            : "border-border bg-surface text-muted hover:border-gold/40 hover:text-foreground"}`}>
          <BellRing aria-hidden="true" className="size-4" /> Follow-up due
        </Link>
        <form action="/admin/leads" role="search"
          className="flex w-full items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold sm:ml-auto sm:w-auto">
          <input type="hidden" name="owner" value={owner} />
          {stage && <input type="hidden" name="stage" value={stage} />}
          {due && <input type="hidden" name="due" value="true" />}
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="q" type="search" defaultValue={q} placeholder="Name, email or LD-…" aria-label="Search leads"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none sm:w-56" />
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
        </form>
      </div>

      {leads.content.length === 0 ? (
        <EmptyState title="No leads here" description={stage || due || q ? "Nothing matches these filters." : undefined} />
      ) : (
        // A list rather than a wide table: one row per lead on wide screens, a card on narrow ones, no sideways scrolling.
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          {leads.content.map((l) => {
            const st = STAGE_STYLE[l.stage];
            const contact = l.email ?? (l.phone ? formatPhone(l.phone) : null);
            const followUp = l.nextFollowUpAt?.slice(0, 10);
            const overdue = Boolean(followUp && followUp <= today && l.stage !== "WON" && l.stage !== "LOST");
            return (
              <li key={l.id}
                className="group grid gap-3 p-4 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:px-5">
                <div className="flex min-w-0 items-center gap-3">
                  <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-105 ${st.tile}`}>
                    <st.icon className="size-4" />
                  </span>
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-baseline gap-x-2">
                      <Link href={`/admin/leads/${l.id}`} className="card-link truncate font-medium text-foreground group-hover:text-gold-text">
                        {l.firstName} {l.lastName}
                      </Link>
                      <span className="font-mono text-xs text-muted">{l.reference}</span>
                    </p>
                    <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                      {contact && (
                        <span className="inline-flex min-w-0 items-center gap-1">
                          {l.email ? <Mail aria-hidden="true" className="size-3.5 shrink-0" /> : <Phone aria-hidden="true" className="size-3.5 shrink-0" />}
                          <span className="break-all"><EmailText>{contact}</EmailText></span>
                        </span>
                      )}
                      <span className="inline-flex items-center gap-1">
                        {l.source === "WEBSITE" ? <Globe aria-hidden="true" className="size-3.5" /> : <UserRound aria-hidden="true" className="size-3.5" />}
                        {l.source === "WEBSITE" ? "Website" : "Employee"}
                      </span>
                      <span>Updated {formatDateTime(l.updatedAt)}</span>
                    </p>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2 pl-[3.25rem] sm:justify-end sm:pl-0">
                  {followUp && (
                    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${overdue
                      ? "bg-rose-500/10 text-rose-700 ring-1 ring-rose-500/20 dark:text-rose-300" : "bg-background text-muted ring-1 ring-border"}`}>
                      <CalendarClock aria-hidden="true" className="size-3" /> {formatDate(l.nextFollowUpAt)}
                    </span>
                  )}
                  {l.ownerName ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-background px-2.5 py-0.5 text-xs font-medium ring-1 ring-border">
                      <UserRound aria-hidden="true" className="size-3" /> {l.ownerName}
                    </span>
                  ) : (
                    <span className="inline-flex rounded-full border border-dashed border-border px-2.5 py-0.5 text-xs text-muted">Unassigned</span>
                  )}
                  <StatusBadge tone={STAGE_TONE[l.stage]}>{STAGE_LABEL[l.stage]}</StatusBadge>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <Pagination page={leads} basePath="/admin/leads" params={{ owner, stage, due: due ? "true" : undefined, q: q || undefined }} />
    </div>
  );
}
