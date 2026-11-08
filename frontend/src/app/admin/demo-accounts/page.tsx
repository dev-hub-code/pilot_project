import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import {
  CalendarDays, CalendarRange, ChevronRight, FlaskConical, Info, type LucideIcon, MousePointerClick, Network, Package, ShieldOff, Wallet,
} from "lucide-react";
import { EmailText } from "@/components/ui/email-text";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { CreateDemoAccountForm } from "@/features/admin/demo-forms";
import { InvestorPicker } from "@/features/admin/investor-picker";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DemoAccountSummary } from "@/types/demo";
import type { AdminUserDetail, AdminUserSummary } from "@/types/user";
import { formatDate } from "@/utils/format";

export const metadata: Metadata = { title: "Demo accounts" };

const HERE = "/admin/demo-accounts";
const RULES: { icon: LucideIcon; text: string }[] = [
  { icon: CalendarDays, text: "Returns are paid out on the 1st and referral commission on the 5th of every month, worked out from the percentages." },
  { icon: Wallet, text: "Their own pages show these figures as if they were real." },
  { icon: ShieldOff, text: "Demo accounts are never paid out for real." },
  { icon: Info, text: "Only super administrators can open this page." },
];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** YYYY-MM-DD of a local date. */
function isoDay(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/**
 * Super administrators mark investors as demo accounts (?find= to search, ?pick= to choose one) and open them to give
 * them dummy orders and a downline.
 */
export default async function DemoAccountsPage({ searchParams }: PageProps<"/admin/demo-accounts">) {
  const denied = await deniedUnlessInMenu("/admin/demo-accounts");
  if (denied) return denied;
  const query = await searchParams;
  const find = typeof query.find === "string" ? query.find.trim().slice(0, 100) : "";
  const pickId = typeof query.pick === "string" && UUID.test(query.pick) ? query.pick : null;

  const [accounts, found] = await Promise.all([
    authFetch<DemoAccountSummary[]>("/api/v1/admin/demo-accounts"),
    find ? authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/users?kind=INVESTOR&size=10&q=${encodeURIComponent(find)}`)
      .then((p) => p.content) : null,
  ]);
  const demoIds = new Set(accounts.map((a) => a.userId));
  const picked = pickId
    ? await authFetch<AdminUserDetail>(`/api/v1/admin/users/${pickId}`).then((d) => d.summary, () => null) : null;
  // A year up to the end of this month, by default.
  const now = new Date();
  const defaultEnd = isoDay(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  const defaultStart = isoDay(new Date(now.getFullYear() - 1, now.getMonth() + 1, 1));

  const totals = {
    accounts: accounts.length,
    orders: accounts.reduce((n, a) => n + a.orders, 0),
    members: accounts.reduce((n, a) => n + a.members, 0),
  };

  return (
    <div className="space-y-6">
      <PageHeader title="Demo accounts"
        description="Give an investor dummy orders, a dummy downline and payouts over a time frame, so their pages show real-looking figures." />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <ul className="grid gap-3 sm:grid-cols-3">
          {[
            { icon: FlaskConical, label: "Demo accounts", value: totals.accounts },
            { icon: Package, label: "Dummy orders", value: totals.orders },
            { icon: Network, label: "Downline members", value: totals.members },
          ].map(({ icon: Icon, label, value }) => (
            <li key={label} className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
                <Icon className="size-5" />
              </span>
              <span>
                <span className="block text-2xl font-semibold tracking-tight tabular-nums">{value}</span>
                <span className="text-xs text-muted">{label}</span>
              </span>
            </li>
          ))}
        </ul>
        <aside className="relative isolate overflow-hidden rounded-2xl bg-ink p-5 text-on-ink shadow-xl shadow-ink/20 lg:row-span-2">
          <div aria-hidden="true" className="absolute -top-16 -right-16 -z-10 size-40 rounded-full bg-gold/25 blur-3xl" />
          <h2 className="flex items-center gap-2 font-semibold"><Info aria-hidden="true" className="size-5 text-gold-on-ink" /> How demo figures work</h2>
          <ul className="mt-4 space-y-3 text-sm">
            {RULES.map(({ icon: Icon, text }) => (
              <li key={text} className="flex gap-3">
                <span aria-hidden="true" className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-white/10 text-gold-on-ink">
                  <Icon className="size-4" />
                </span>
                <span className="pt-1 text-on-ink/80">{text}</span>
              </li>
            ))}
          </ul>
        </aside>

        <section aria-labelledby="demo-list" className="min-w-0 overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          <h2 id="demo-list" className="flex items-center gap-3 border-b border-border bg-background/40 px-5 py-4 font-semibold tracking-tight">
            <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
              <FlaskConical className="size-[18px]" />
            </span>
            Demo accounts <span className="text-sm font-normal text-muted">{accounts.length}</span>
          </h2>
          {accounts.length === 0 ? (
            <div className="p-5"><EmptyState title="No demo accounts yet" description="Find an investor below to make one." /></div>
          ) : (
            <ul className="divide-y divide-border">
              {accounts.map((a) => (
                <li key={a.userId} className="grid gap-3 p-4 transition-colors sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                  <div className="flex min-w-0 items-center gap-3">
                    <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-gold/10 text-xs font-semibold text-gold-text">
                      {initials(a.name)}
                    </span>
                    <div className="min-w-0 text-sm">
                      <Link href={`${HERE}/${a.userId}`} className="card-link font-medium hover:text-gold-text">{a.name}</Link>{" "}
                      <span className="font-mono text-xs text-muted">{a.userCode}</span>
                      <span className="block truncate text-xs text-muted"><EmailText>{a.email}</EmailText></span>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 text-xs sm:justify-end">
                    <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 text-muted ring-1 ring-border">
                      <CalendarRange aria-hidden="true" className="size-3.5" /> {formatDate(a.startsOn)} – {formatDate(a.endsOn)}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 tabular-nums text-muted ring-1 ring-border">
                      <Package aria-hidden="true" className="size-3.5" /> {a.orders} {a.orders === 1 ? "order" : "orders"}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 tabular-nums text-muted ring-1 ring-border">
                      <Network aria-hidden="true" className="size-3.5" /> {a.members} {a.members === 1 ? "member" : "members"}
                    </span>
                    <ChevronRight aria-hidden="true" className="hidden size-4 text-muted sm:block" />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <InvestorPicker title="Make an investor a demo account" description="Find the investor by email, name or ID."
          find={find} found={found?.filter((u) => !demoIds.has(u.id)) ?? null}
          hrefFor={(id) => `${HERE}?find=${encodeURIComponent(find)}&pick=${id}`} />
        {picked && !demoIds.has(picked.id) ? (
          <div className="space-y-4 rounded-2xl border border-gold/40 bg-surface p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-gold text-xs font-semibold text-on-gold">
                {initials(`${picked.firstName} ${picked.lastName}`)}
              </span>
              <div className="min-w-0">
                <h2 className="font-semibold">{picked.firstName} {picked.lastName} <span className="font-mono text-xs font-normal text-muted">{picked.userCode}</span></h2>
                <p className="text-sm text-muted">Choose the time frame the dummy figures cover. A year to the end of this month is filled in.</p>
              </div>
            </div>
            <CreateDemoAccountForm userId={picked.id} name={`${picked.firstName} ${picked.lastName}`}
              defaultStart={defaultStart} defaultEnd={defaultEnd} />
          </div>
        ) : (
          <div className="hidden items-center justify-center gap-3 rounded-2xl border border-dashed border-border p-5 text-sm text-muted lg:flex">
            <MousePointerClick aria-hidden="true" className="size-5" /> Pick an investor to set their time frame here.
          </div>
        )}
      </div>
    </div>
  );
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.charAt(0) ?? "") + (parts.length > 1 ? parts.at(-1)?.charAt(0) ?? "" : "")).toUpperCase() || "?";
}
