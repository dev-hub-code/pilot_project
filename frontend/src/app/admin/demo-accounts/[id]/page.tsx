import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmailText } from "@/components/ui/email-text";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import {
  DemoFrameForm, DemoMemberForm, DemoOrderForm, RemoveDemoAccount, RemoveDemoMember, RemoveDemoOrder,
} from "@/features/admin/demo-forms";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DemoAccount, DemoMember } from "@/types/demo";
import { formatDate, formatMonth } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Demo account" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** One demo account: its time frame, dummy orders and downline, and the figures its own pages show. */
export default async function DemoAccountPage({ params }: PageProps<"/admin/demo-accounts/[id]">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.DEMO_ACCOUNT_MANAGE)) notFound();
  const { id } = await params;
  if (!UUID.test(id)) notFound();
  let demo: DemoAccount;
  try {
    demo = await authFetch<DemoAccount>(`/api/v1/admin/demo-accounts/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const own = demo.orders.filter((o) => o.memberId === null);
  const theirs = demo.orders.filter((o) => o.memberId !== null);
  const t = demo.totals;

  return (
    <div className="space-y-6">
      <PageHeader title={`${demo.name} · demo account`}
        description={`Figures as of ${formatDate(demo.today)}: rent and commission credited up to today (or the time frame's end), rent paid out on the 1st and commission on the 5th of each following month. The investor sees these on their own pages; real withdrawal runs leave this account out.`} />
      <p className="text-sm text-muted">
        <span className="font-mono">{demo.userCode}</span> · <EmailText>{demo.email}</EmailText> ·{" "}
        <Link href={`/admin/users/${demo.userId}`} className="text-gold-text hover:underline">User page</Link>
      </p>

      <div className="grid gap-4 sm:grid-cols-3">
        <Figure label="Rent credited" value={formatMoney(t.rentPaid)} note={`${formatMoney(t.rentWithdrawn)} paid out · ${formatMoney(t.rentBalance)} in the wallet`} />
        <Figure label="Referral commission credited" value={formatMoney(t.commissionPaid)}
          note={`${formatMoney(t.referralWithdrawn)} paid out · ${formatMoney(t.referralBalance)} in the wallet`} />
        <Figure label="Commission rates (levels 1-4)" value={demo.rates.map((r) => formatPercent(r, 2)).join(" · ")}
          note="The rates in force now" />
      </div>

      <Card title="Time frame" description="The dummy figures run over these days. Orders and members must fall inside it.">
        <DemoFrameForm userId={demo.userId} startsOn={demo.startsOn} endsOn={demo.endsOn} />
      </Card>

      <Card title={`Own orders · ${own.length}`} description="Rent: price × containers × monthly rent %, for each lease month.">
        {own.length === 0 ? <EmptyState title="No dummy orders yet" /> : (
          <DataTable columns={["Order", "Plan", "Amount", "Rent", "Lease", "Credited", ""]}>
            {own.map((o) => (
              <tr key={o.id}>
                <Cell label="Order" className="font-mono text-xs">{o.orderNumber}</Cell>
                <Cell label="Plan" wrap><span className="font-mono text-xs text-muted">{o.planCode}</span> {o.planTitle}<span className="block text-xs text-muted">{o.containers} × {CONTAINER_TYPE_LABEL[o.containerType]} at {formatMoney(o.pricePerContainer)}</span></Cell>
                <Cell label="Amount" className="tabular-nums">{formatMoney(o.amount)}</Cell>
                <Cell label="Rent" className="tabular-nums">{formatMoney(o.monthlyRent)}/month<span className="block text-xs text-muted">{formatPercent(o.rentPercent, 2)}</span></Cell>
                <Cell label="Lease">{formatDate(o.startsOn)} – {formatDate(o.endsOn)}<span className="block text-xs text-muted">{o.tenureMonths} months</span></Cell>
                <Cell label="Credited" className="tabular-nums">{formatMoney(o.earned)}</Cell>
                <Cell><RemoveDemoOrder userId={demo.userId} orderId={o.id} orderNumber={o.orderNumber} /></Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <Card title={`Downline · ${demo.members.length}`}
        description="Each member's orders earn the investor that level's rate of the member's investment, every lease month. Levels 5 and below earn nothing.">
        {demo.members.length === 0 ? <EmptyState title="No dummy downline yet" /> : <Tree members={demo.members} userId={demo.userId} />}
        {theirs.length > 0 && (
          <div className="mt-6 space-y-2">
            <h3 className="text-sm font-semibold">Downline orders</h3>
            <DataTable columns={["Member", "Order", "Amount", "Lease", ""]}>
              {theirs.map((o) => (
                <tr key={o.id}>
                  <Cell label="Member">{o.memberName}</Cell>
                  <Cell label="Order" wrap><span className="font-mono text-xs">{o.orderNumber}</span> · {o.planCode} {o.planTitle}</Cell>
                  <Cell label="Amount" className="tabular-nums">{formatMoney(o.amount)}</Cell>
                  <Cell label="Lease">{formatDate(o.startsOn)} – {formatDate(o.endsOn)}</Cell>
                  <Cell><RemoveDemoOrder userId={demo.userId} orderId={o.id} orderNumber={o.orderNumber} /></Cell>
                </tr>
              ))}
            </DataTable>
          </div>
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Add an order">
          <DemoOrderForm userId={demo.userId} members={demo.members} plans={demo.plans} rentBands={demo.rentBands}
            startsOn={demo.startsOn} />
        </Card>
        <Card title="Add a downline member">
          <DemoMemberForm userId={demo.userId} members={demo.members} joinedOn={demo.startsOn} />
        </Card>
      </div>

      <Card title="Month by month" description="Credited so far, and still to come within the time frame.">
        <DataTable columns={["Month", "Rent credited", "Rent to come", "Commission credited", "Commission to come"]}>
          {demo.months.map((m) => (
            <tr key={m.month}>
              <Cell label="Month">{formatMonth(m.month)}</Cell>
              <Cell label="Rent credited" className="tabular-nums">{formatMoney(m.rentPaid)}</Cell>
              <Cell label="Rent to come" className="tabular-nums text-muted">{formatMoney(m.rentScheduled)}</Cell>
              <Cell label="Commission credited" className="tabular-nums">{formatMoney(m.commission)}</Cell>
              <Cell label="Commission to come" className="tabular-nums text-muted">{formatMoney(m.commissionScheduled)}</Cell>
            </tr>
          ))}
        </DataTable>
      </Card>

      <Card title={`Payouts · ${demo.payouts.length}`} description="Shown to the investor as paid withdrawals.">
        {demo.payouts.length === 0 ? <EmptyState title="No payouts yet" description="The first is on the 1st of the month after rent is earned." /> : (
          <DataTable columns={["Paid on", "Kind", "Earned in", "Amount"]}>
            {[...demo.payouts].reverse().map((p) => (
              <tr key={`${p.kind}-${p.earnedIn}`}>
                <Cell label="Paid on">{formatDate(p.paidOn)}</Cell>
                <Cell label="Kind">{p.kind === "RENT" ? "Rent" : "Referral commission"}</Cell>
                <Cell label="Earned in">{formatMonth(p.earnedIn)}</Cell>
                <Cell label="Amount" className="tabular-nums">{formatMoney(p.amount)}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>

      <Notice tone="warning">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span>Stopping deletes every dummy order and member; the investor&apos;s pages show their real figures again.</span>
          <RemoveDemoAccount userId={demo.userId} name={demo.name} />
        </div>
      </Notice>
    </div>
  );
}

function Figure({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="border border-border bg-surface p-4">
      <p className="text-xs tracking-[0.08em] text-muted uppercase">{label}</p>
      <p className="mt-1 text-xl font-semibold tabular-nums">{value}</p>
      <p className="mt-1 text-xs text-muted">{note}</p>
    </div>
  );
}

type Node = DemoMember & { children: Node[] };

function Tree({ members, userId }: { members: DemoMember[]; userId: string }) {
  const nodes = new Map<string, Node>(members.map((m) => [m.id, { ...m, children: [] }]));
  const top: Node[] = [];
  for (const node of nodes.values()) (node.parentId ? nodes.get(node.parentId)?.children ?? top : top).push(node);
  return <Branch nodes={top} userId={userId} />;
}

function Branch({ nodes, userId, nested = false }: { nodes: Node[]; userId: string; nested?: boolean }) {
  return (
    <ul className={nested ? "ml-4 space-y-2 border-l border-border pl-4" : "space-y-2"}>
      {nodes.map((n) => (
        <li key={n.id} className="space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2 border border-border bg-surface px-4 py-2.5 text-sm">
            <span className="flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border px-2 py-0.5 text-[11px] tracking-[0.08em] text-muted uppercase">
                Level {n.level}{n.level > 4 ? " · no commission" : ""}
              </span>
              <span className="font-medium">{n.name}</span>
              <span className="text-xs text-muted">joined {formatDate(n.joinedOn)} · earned the investor {formatMoney(n.commission)}</span>
            </span>
            <RemoveDemoMember userId={userId} memberId={n.id} name={n.name} />
          </div>
          {n.children.length > 0 && <Branch nodes={n.children} userId={userId} nested />}
        </li>
      ))}
    </ul>
  );
}
