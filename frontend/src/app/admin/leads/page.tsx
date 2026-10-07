import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
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

export const metadata: Metadata = { title: "Leads" };

export default async function LeadsPage({ searchParams }: PageProps<"/admin/leads">) {
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

  return (
    <div className="space-y-6">
      <PageHeader title="Leads" description={manager ? "The whole sales pipeline." : "Your leads and unassigned enquiries."}
        actions={can(Permission.LEAD_CREATE) ? <LinkButton href="/admin/leads/new">New lead</LinkButton> : undefined} />

      <section aria-label="Pipeline" className="grid gap-px bg-border sm:grid-cols-3 lg:grid-cols-6">
        {pipeline.map((p) => (
          <Link key={p.stage} href={link({ stage: p.stage === stage ? undefined : p.stage })}
            aria-current={p.stage === stage ? "page" : undefined}
            className={`space-y-1 bg-surface p-4 hover:bg-background ${p.stage === stage ? "outline outline-2 -outline-offset-2 outline-gold" : ""}`}>
            <p className="text-xs uppercase tracking-[0.1em] text-muted">{STAGE_LABEL[p.stage as LeadStage]}</p>
            <p className="font-display text-3xl font-semibold tabular-nums">{p.leads}</p>
            <p className="truncate text-xs text-muted">{p.value.map((m) => formatMoney(m, { compact: true })).join(" · ") || "—"}</p>
          </Link>
        ))}
      </section>

      <div className="flex flex-wrap items-center gap-2 text-sm">
        {owners.map((o) => (
          <Link key={o} href={link({ owner: o })} aria-current={o === owner ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${o === owner ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {o === "all" ? (manager ? "Everyone" : "All I can see") : o === "me" ? "Mine" : "Unassigned"}
          </Link>
        ))}
        <Link href={link({ due: due ? undefined : "true" })} aria-current={due ? "page" : undefined}
          className={`rounded-full border px-3 py-1 ${due ? "border-foreground text-foreground" : "border-border text-muted"}`}>
          Follow-up due
        </Link>
        <form action="/admin/leads" className="ml-auto flex gap-2">
          <input type="hidden" name="owner" value={owner} />
          <input name="q" defaultValue={q} placeholder="Name, email or LD-…" aria-label="Search leads"
            className="h-9 w-56 rounded-none border border-border bg-surface px-3 text-sm" />
        </form>
      </div>

      {leads.content.length === 0 ? (
        <EmptyState title="No leads here" />
      ) : (
        <DataTable columns={["Lead", "Contact", "Source", "Stage", "Owner", "Follow-up", "Updated"]}>
          {leads.content.map((l) => (
            <tr key={l.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/leads/${l.id}`} className="font-medium text-gold-text hover:underline">
                  {l.firstName} {l.lastName}
                </Link>
                <span className="block font-mono text-xs text-muted">{l.reference}</span>
              </Cell>
              <Cell className="text-muted">{l.email ?? l.phone}</Cell>
              <Cell className="text-muted">{l.source === "WEBSITE" ? "Website" : "Staff"}</Cell>
              <Cell><StatusBadge tone={STAGE_TONE[l.stage]}>{STAGE_LABEL[l.stage]}</StatusBadge></Cell>
              <Cell>{l.ownerName ?? <span className="text-muted">Unassigned</span>}</Cell>
              <Cell className="text-muted">{formatDate(l.nextFollowUpAt)}</Cell>
              <Cell className="text-muted">{formatDateTime(l.updatedAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={leads} basePath="/admin/leads" params={{ owner, stage, due: due ? "true" : undefined, q: q || undefined }} />
    </div>
  );
}
