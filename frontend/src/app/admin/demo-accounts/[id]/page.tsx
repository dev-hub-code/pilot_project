import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  AlertTriangle, CalendarDays, CalendarRange, Clock, FlaskConical, Handshake, type LucideIcon, Network, Package, PackagePlus, Percent,
  TrendingUp, UserPlus, UserRound, Wallet,
} from "lucide-react";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmailText } from "@/components/ui/email-text";
import { EmptyState } from "@/components/ui/empty-state";
import {
  DemoFrameForm, DemoMemberForm, DemoOrderForm, RemoveDemoAccount, RemoveDemoMember, RemoveDemoOrder,
} from "@/features/admin/demo-forms";
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
      <section className="relative isolate overflow-hidden rounded-3xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 sm:p-8">
        <div aria-hidden="true" className="absolute -top-24 -right-20 -z-10 size-72 rounded-full bg-gold/25 blur-3xl" />
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-4">
            <span aria-hidden="true" className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-gold text-lg font-semibold text-on-gold">
              {initials(demo.name)}
            </span>
            <div className="min-w-0">
              <p className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-2.5 py-0.5 text-xs font-medium text-gold-on-ink">
                <FlaskConical aria-hidden="true" className="size-3.5" /> Demo account
              </p>
              <h1 className="mt-1.5 text-2xl font-semibold tracking-tight break-words">{demo.name}</h1>
              <p className="mt-0.5 text-sm text-on-ink/70">
                <span className="font-mono">{demo.userCode}</span> · <EmailText>{demo.email}</EmailText>
              </p>
            </div>
          </div>
          <Link href={`/admin/users/${demo.userId}`}
            className="inline-flex items-center gap-1.5 rounded-xl bg-white/10 px-3.5 py-2 text-sm font-medium transition-colors hover:bg-white/15">
            <UserRound aria-hidden="true" className="size-4" /> User page
          </Link>
        </div>
        <ul className="mt-6 flex flex-wrap gap-2 text-xs">
          <li className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
            <CalendarRange aria-hidden="true" className="size-3.5 text-gold-on-ink" /> {formatDate(demo.startsOn)} – {formatDate(demo.endsOn)}
          </li>
          <li className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
            <Clock aria-hidden="true" className="size-3.5 text-gold-on-ink" /> Figures as of {formatDate(demo.today)}
          </li>
          <li className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
            <Package aria-hidden="true" className="size-3.5 text-gold-on-ink" /> {demo.orders.length} dummy {demo.orders.length === 1 ? "order" : "orders"}
          </li>
          <li className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
            <Network aria-hidden="true" className="size-3.5 text-gold-on-ink" /> {demo.members.length} downline {demo.members.length === 1 ? "member" : "members"}
          </li>
        </ul>
        <p className="mt-4 max-w-3xl text-xs text-on-ink/60">
          Returns are paid out on the 1st and commission on the 5th of each following month. The investor sees these figures on their own pages; real withdrawal runs leave this account out.
        </p>
      </section>

      <div className="grid gap-4 sm:grid-cols-3">
        <Figure icon={TrendingUp} label="Returns credited" value={formatMoney(t.rentPaid)}
          rows={[["Paid out", formatMoney(t.rentWithdrawn)], ["In the wallet", formatMoney(t.rentBalance)]]} />
        <Figure icon={Handshake} label="Referral commission credited" value={formatMoney(t.commissionPaid)}
          rows={[["Paid out", formatMoney(t.referralWithdrawn)], ["In the wallet", formatMoney(t.referralBalance)]]} />
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="flex items-center gap-2 text-sm text-muted">
            <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><Percent className="size-4" /></span>
            Commission rates in force
          </p>
          <ul className="mt-3 grid grid-cols-4 gap-2">
            {demo.rates.map((r, i) => (
              <li key={i} className="rounded-xl bg-background/60 px-2 py-2 text-center ring-1 ring-border">
                <span className="block text-[11px] text-muted">L{i + 1}</span>
                <span className="text-sm font-semibold tabular-nums">{formatPercent(r, 2)}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="min-w-0 space-y-6">
          <Section icon={Package} title="Own orders" count={own.length} description="Returns: amount × monthly returns %, for each month of the term.">
            {own.length === 0 ? <div className="p-5"><EmptyState title="No dummy orders yet" description="Add one from “Add an order”." /></div> : (
              <ul className="divide-y divide-border">
                {own.map((o) => (
                  <li key={o.id} className="grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                    <div className="min-w-0 text-sm">
                      <p className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-xs font-medium">{o.orderNumber}</span>
                        <span className="rounded-full bg-gold/10 px-2 py-0.5 font-mono text-[11px] text-gold-text">{o.planCode}</span>
                        <span className="font-medium">{o.planTitle}</span>
                      </p>
                      <p className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                        <span className="inline-flex items-center gap-1"><CalendarRange aria-hidden="true" className="size-3.5" /> {formatDate(o.startsOn)} – {formatDate(o.endsOn)} · {o.tenureMonths} months</span>
                        <span className="tabular-nums">{formatMoney(o.monthlyRent)}/month · {formatPercent(o.rentPercent, 2)}</span>
                      </p>
                    </div>
                    <div className="flex items-center justify-between gap-4 sm:justify-end">
                      <dl className="flex gap-4 text-right text-sm">
                        <div><dt className="text-[11px] text-muted">Amount</dt><dd className="font-semibold tabular-nums">{formatMoney(o.amount)}</dd></div>
                        <div><dt className="text-[11px] text-muted">Credited</dt><dd className="font-semibold tabular-nums text-emerald-700 dark:text-emerald-300">{formatMoney(o.earned)}</dd></div>
                      </dl>
                      <RemoveDemoOrder userId={demo.userId} orderId={o.id} orderNumber={o.orderNumber} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Section>

          <Section icon={Network} title="Downline" count={demo.members.length}
            description="Each member's orders earn the investor that level's rate of the member's investment, every term month. Levels 5 and below earn nothing.">
            <div className="space-y-6 p-5">
              {demo.members.length === 0 ? <EmptyState title="No dummy downline yet" description="Add one from “Add a downline member”." />
                : <Tree members={demo.members} userId={demo.userId} />}
              {theirs.length > 0 && (
                <div className="space-y-2">
                  <h3 className="text-sm font-semibold">Downline orders <span className="font-normal text-muted">{theirs.length}</span></h3>
                  <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border">
                    {theirs.map((o) => (
                      <li key={o.id} className="grid gap-2 p-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                        <div className="flex min-w-0 items-center gap-3 text-sm">
                          <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-gold/10 text-[11px] font-semibold text-gold-text">
                            {initials(o.memberName ?? "")}
                          </span>
                          <div className="min-w-0">
                            <p className="font-medium">{o.memberName}</p>
                            <p className="text-xs break-words text-muted">
                              <span className="font-mono">{o.orderNumber}</span> · {o.planCode} {o.planTitle} · {formatDate(o.startsOn)} – {formatDate(o.endsOn)}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center justify-between gap-4 sm:justify-end">
                          <span className="text-sm font-semibold tabular-nums">{formatMoney(o.amount)}</span>
                          <RemoveDemoOrder userId={demo.userId} orderId={o.id} orderNumber={o.orderNumber} />
                        </div>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </Section>

          <Section icon={CalendarDays} title="Month by month" description="Credited so far, and still to come within the time frame.">
            <DataTable columns={["Month", "Returns credited", "Returns to come", "Commission credited", "Commission to come"]}>
              {demo.months.map((m) => (
                <tr key={m.month}>
                  <Cell label="Month" className="font-medium">{formatMonth(m.month)}</Cell>
                  <Cell label="Returns credited" className="tabular-nums">{formatMoney(m.rentPaid)}</Cell>
                  <Cell label="Returns to come" className="tabular-nums text-muted">{formatMoney(m.rentScheduled)}</Cell>
                  <Cell label="Commission credited" className="tabular-nums">{formatMoney(m.commission)}</Cell>
                  <Cell label="Commission to come" className="tabular-nums text-muted">{formatMoney(m.commissionScheduled)}</Cell>
                </tr>
              ))}
            </DataTable>
          </Section>

          <Section icon={Wallet} title="Payouts" count={demo.payouts.length} description="Shown to the investor as paid withdrawals.">
            {demo.payouts.length === 0 ? (
              <div className="p-5"><EmptyState title="No payouts yet" description="The first is on the 1st of the month after returns are earned." /></div>
            ) : (
              <ul className="divide-y divide-border">
                {[...demo.payouts].reverse().map((p) => (
                  <li key={`${p.kind}-${p.earnedIn}`} className="flex items-center gap-3 px-5 py-3 text-sm">
                    <span aria-hidden="true" className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${p.kind === "RENT"
                      ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300" : "bg-gold/10 text-gold-text"}`}>
                      {p.kind === "RENT" ? <TrendingUp className="size-4" /> : <Handshake className="size-4" />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-medium">{p.kind === "RENT" ? "Returns" : "Referral commission"}</p>
                      <p className="text-xs text-muted">Paid {formatDate(p.paidOn)} · earned in {formatMonth(p.earnedIn)}</p>
                    </div>
                    <span className="font-semibold tabular-nums">{formatMoney(p.amount)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        </div>

        <aside className="space-y-6">
          <Panel icon={CalendarRange} title="Time frame" description="The dummy figures run over these days. Orders and members must fall inside it.">
            <DemoFrameForm userId={demo.userId} startsOn={demo.startsOn} endsOn={demo.endsOn} />
          </Panel>
          <Panel icon={PackagePlus} title="Add an order">
            <DemoOrderForm userId={demo.userId} members={demo.members} plans={demo.plans} startsOn={demo.startsOn} />
          </Panel>
          <Panel icon={UserPlus} title="Add a downline member">
            <DemoMemberForm userId={demo.userId} members={demo.members} joinedOn={demo.startsOn} />
          </Panel>
          <div className="space-y-3 rounded-2xl border border-red-500/30 bg-red-500/5 p-5">
            <h2 className="flex items-center gap-2 font-semibold text-red-700 dark:text-red-300">
              <AlertTriangle aria-hidden="true" className="size-5" /> Stop the demo
            </h2>
            <p className="text-sm text-muted">Deletes every dummy order and member; the investor&apos;s pages show their real figures again.</p>
            <RemoveDemoAccount userId={demo.userId} name={demo.name} />
          </div>
        </aside>
      </div>
    </div>
  );
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.charAt(0) ?? "") + (parts.length > 1 ? parts.at(-1)?.charAt(0) ?? "" : "")).toUpperCase() || "?";
}

function Figure({ icon: Icon, label, value, rows }: { icon: LucideIcon; label: string; value: string; rows: [string, string][] }) {
  return (
    <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
      <p className="flex items-center gap-2 text-sm text-muted">
        <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><Icon className="size-4" /></span>
        {label}
      </p>
      <p className="mt-2 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
      <dl className="mt-3 grid grid-cols-2 gap-2 border-t border-border pt-3 text-xs">
        {rows.map(([k, v]) => (
          <div key={k}><dt className="text-muted">{k}</dt><dd className="font-medium tabular-nums">{v}</dd></div>
        ))}
      </dl>
    </div>
  );
}

function Section({ icon: Icon, title, count, description, children }: {
  icon: LucideIcon; title: string; count?: number; description?: string; children: React.ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
      <div className="flex items-start gap-3 border-b border-border bg-background/40 px-5 py-4">
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
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

function Panel({ icon: Icon, title, description, children }: {
  icon: LucideIcon; title: string; description?: string; children: React.ReactNode;
}) {
  return (
    <section className="space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm">
      <div className="flex items-start gap-3">
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text"><Icon className="size-[18px]" /></span>
        <div className="min-w-0">
          <h2 className="font-semibold">{title}</h2>
          {description && <p className="text-xs text-muted">{description}</p>}
        </div>
      </div>
      {children}
    </section>
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
    <ul className={nested ? "ml-4 space-y-2 border-l-2 border-gold/30 pl-4" : "space-y-2"}>
      {nodes.map((n) => (
        <li key={n.id} className="space-y-2">
          <div className={`flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border bg-surface px-3 py-2.5 text-sm shadow-sm ${n.level > 4 ? "opacity-70" : ""}`}>
            <span className="flex min-w-0 items-center gap-3">
              <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-gold/10 text-[11px] font-semibold text-gold-text">
                {initials(n.name)}
              </span>
              <span className="min-w-0">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{n.name}</span>
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${n.level > 4
                    ? "bg-background text-muted ring-1 ring-border" : "bg-ink text-on-ink dark:bg-gold dark:text-on-gold"}`}>
                    Level {n.level}{n.level > 4 ? " · no commission" : ""}
                  </span>
                </span>
                <span className="block text-xs text-muted">Joined {formatDate(n.joinedOn)} · earned the investor <span className="font-medium text-foreground tabular-nums">{formatMoney(n.commission)}</span></span>
              </span>
            </span>
            <RemoveDemoMember userId={userId} memberId={n.id} name={n.name} />
          </div>
          {n.children.length > 0 && <Branch nodes={n.children} userId={userId} nested />}
        </li>
      ))}
    </ul>
  );
}
