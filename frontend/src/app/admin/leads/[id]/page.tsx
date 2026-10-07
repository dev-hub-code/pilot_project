import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { claimLeadAction } from "@/features/admin/lead-actions";
import { ActivityForm, AssignForm, LeadForm, StageForm } from "@/features/admin/lead-forms";
import { ACTIVITY_LABEL, INTEREST_LABEL, STAGE_LABEL, STAGE_TONE } from "@/features/leads/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Assignee, LeadDetail } from "@/types/lead";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Lead" };

export default async function LeadPage({ params }: PageProps<"/admin/leads/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let detail: LeadDetail;
  try {
    detail = await authFetch<LeadDetail>(`/api/v1/admin/leads/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { lead: l, editable, activities } = detail;
  const manager = can(Permission.LEAD_ASSIGN);
  const assignees = manager ? await authFetch<Assignee[]>("/api/v1/admin/leads/assignees") : [];
  const canWork = editable && can(Permission.LEAD_UPDATE);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <Link href="/admin/leads" className="text-sm text-muted hover:underline">Leads</Link>
          <h1 className="text-3xl font-semibold tracking-tight">{l.firstName} {l.lastName}</h1>
          <p className="font-mono text-sm text-muted">{l.reference} · {l.source === "WEBSITE" ? "website enquiry" : "entered by staff"}</p>
        </div>
        <StatusBadge tone={STAGE_TONE[l.stage]}>{STAGE_LABEL[l.stage]}</StatusBadge>
      </div>

      {!editable && !l.ownerId && can(Permission.LEAD_UPDATE) && (
        <Card title="Unassigned" description="Claim this lead to start working on it.">
          <ConfirmForm action={claimLeadAction.bind(null, l.id)} submitLabel="Claim lead" confirm={`Take ${l.reference}?`} />
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <Card title="Details">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Email">{l.email ?? "—"}</Item>
              <Item label="Phone">{l.phone ?? "—"}</Item>
              <Item label="Country">{l.country ?? "—"}</Item>
              <Item label="Interested in">{INTEREST_LABEL[l.interest]}</Item>
              <Item label="Estimate">{formatMoney(l.estimate)}</Item>
              <Item label="Owner">{l.ownerName ?? "Unassigned"}</Item>
              <Item label="Next follow-up">{formatDateTime(l.nextFollowUpAt)}</Item>
              <Item label="Investor account">
                {l.userId ? <Link href={`/admin/users/${l.userId}`} className="text-gold-text hover:underline">View account</Link> : "Not registered"}
              </Item>
              {l.won && <Item label="Invested">{formatMoney(l.won)}</Item>}
              {l.lostReason && <Item label="Lost because">{l.lostReason}</Item>}
              {l.message && <div className="sm:col-span-3"><Item label="Their message">{l.message}</Item></div>}
            </dl>
          </Card>

          <section className="space-y-3" aria-label="Activity">
            <h2 className="text-lg font-semibold tracking-tight">Activity</h2>
            <ol className="space-y-2">
              {activities.map((a) => (
                <li key={a.id} className="border border-border bg-surface px-4 py-3 text-sm">
                  <p className="text-xs text-muted">
                    <span className="font-medium uppercase tracking-[0.08em]">{ACTIVITY_LABEL[a.type]}</span>
                    {" · "}{formatDateTime(a.createdAt)}{a.actorName ? ` · ${a.actorName}` : ""}
                  </p>
                  <p className="mt-1 whitespace-pre-line">{a.body}</p>
                </li>
              ))}
            </ol>
          </section>

          {canWork && (
            <Card title="Edit details"><LeadForm lead={l} /></Card>
          )}
        </div>

        <div className="space-y-6">
          {l.stage === "WON" && <Notice tone="success">Won{l.won ? ` with a first investment of ${formatMoney(l.won)}` : ""}.</Notice>}
          {canWork && l.stage !== "WON" && <Card title="Stage"><StageForm leadId={l.id} current={l.stage} /></Card>}
          {canWork && <Card title="Log activity"><ActivityForm leadId={l.id} /></Card>}
          {manager && <Card title="Assignment"><AssignForm leadId={l.id} ownerId={l.ownerId} assignees={assignees} /></Card>}
        </div>
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}
