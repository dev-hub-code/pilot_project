import type { Metadata } from "next";
import Link from "next/link";
import {
  AlertTriangle, BarChart3, Briefcase, Building2, Clock, FileSignature, Handshake, Headset, KeyRound, Landmark, Layers, Lock,
  type LucideIcon, Package, Search, Shield, ShieldCheck, ShoppingCart, Target, UserRound, Wallet, X,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { readableCode } from "@/features/admin/role-labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PermissionEntry } from "@/types/admin-portal";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Permissions" };

const CATEGORY_ICON: Record<string, LucideIcon> = {
  ACCESS: KeyRound, SECURITY: Shield, INVESTOR: UserRound, INVESTMENT: Briefcase, CONTAINER: Package, CONTAINERS: Package,
  ORDERS: ShoppingCart, AGREEMENTS: FileSignature, FINANCE: Landmark, PAYOUT: Wallet, WITHDRAWALS: Wallet, BANK_ACCOUNTS: Landmark,
  COMPLIANCE: ShieldCheck, SUPPORT: Headset, CRM: Target, REFERRALS: Handshake, REPORTS: BarChart3, OTHER: Layers,
};

const SHOWS = [
  { key: "all", label: "All" },
  { key: "sensitive", label: "Sensitive" },
  { key: "reserved", label: "Reserved" },
] as const;

export default async function PermissionsPage({ searchParams }: PageProps<"/platform/permissions">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Permissions are for admins." />;
  }
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const show = SHOWS.find((s) => s.key === params.show)?.key ?? "all";
  const catalog = await authFetch<PermissionEntry[]>("/api/v1/admin/permissions");
  const needle = q.toLowerCase();
  const matching = catalog.filter((p) => (show === "all" || (show === "sensitive" ? p.sensitive : p.reserved))
    && (!needle || p.code.toLowerCase().includes(needle) || p.description.toLowerCase().includes(needle)
      || readableCode(p.code).toLowerCase().includes(needle)));
  const categories = [...new Set(matching.map((p) => p.category))];
  const link = (next: { show?: string; q?: string }) => {
    const query = new URLSearchParams();
    const s = next.show ?? show;
    const term = next.q ?? q;
    if (s !== "all") query.set("show", s);
    if (term) query.set("q", term);
    const qs = query.toString();
    return qs ? `/platform/permissions?${qs}` : "/platform/permissions";
  };
  const count = (key: (typeof SHOWS)[number]["key"]) =>
    key === "all" ? catalog.length : catalog.filter((p) => (key === "sensitive" ? p.sensitive : p.reserved)).length;

  return (
    <div className="space-y-6">
      <PageHeader title="Permissions"
        description={`Everything a role can grant · ${catalog.length} permissions. Reserved ones have no feature behind them yet, so granting one changes nothing.`} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Show" className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {SHOWS.map((s) => (
            <Link key={s.key} href={link({ show: s.key })} aria-current={s.key === show ? "page" : undefined}
              className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${s.key === show
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-background hover:text-foreground"}`}>
              {s.key === "sensitive" && <AlertTriangle aria-hidden="true" className="size-4" />}
              {s.key === "reserved" && <Clock aria-hidden="true" className="size-4" />}
              {s.label}
              <span className="text-xs tabular-nums opacity-70">{count(s.key)}</span>
            </Link>
          ))}
        </nav>
        <form role="search" className="flex w-full items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold sm:w-auto">
          {show !== "all" && <input type="hidden" name="show" value={show} />}
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="q" type="search" defaultValue={q} placeholder="Find a permission" aria-label="Find a permission"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none sm:w-64" />
          {q && (
            <Link href={link({ q: "" })} aria-label="Clear search" title="Clear search"
              className="flex size-9 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-background hover:text-foreground">
              <X aria-hidden="true" className="size-4" />
            </Link>
          )}
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
        </form>
      </div>

      {categories.length > 1 && (
        <nav aria-label="Categories" className="flex flex-wrap gap-2 text-sm">
          {categories.map((c) => {
            const Icon = CATEGORY_ICON[c] ?? Building2;
            return (
              <a key={c} href={`#cat-${c}`}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 font-medium text-muted shadow-sm transition-colors hover:border-gold/40 hover:text-foreground">
                <Icon aria-hidden="true" className="size-3.5 text-gold-text" /> {humanize(c)}
                <span className="rounded-full bg-background px-1.5 text-[11px] tabular-nums ring-1 ring-border">
                  {matching.filter((p) => p.category === c).length}
                </span>
              </a>
            );
          })}
        </nav>
      )}

      {matching.length === 0 ? (
        <EmptyState title="No permission matches" description={q ? `Nothing matches “${q}”.` : undefined} />
      ) : categories.map((category) => {
        const Icon = CATEGORY_ICON[category] ?? Building2;
        const items = matching.filter((p) => p.category === category);
        return (
          <section key={category} id={`cat-${category}`} aria-labelledby={`cat-${category}-title`}
            className="scroll-mt-6 overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
            <h2 id={`cat-${category}-title`} className="flex items-center gap-3 border-b border-border bg-background/40 px-5 py-4 font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                <Icon className="size-[18px]" />
              </span>
              {humanize(category)}
              <span className="text-sm font-normal text-muted">{items.length}</span>
            </h2>
            <ul className="grid gap-px bg-border md:grid-cols-2">
              {items.map((p) => (
                <li key={p.code} className={`flex gap-3 bg-surface p-4 ${p.reserved ? "opacity-70" : ""}`}>
                  <span aria-hidden="true" className={`mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg ${p.sensitive
                    ? "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300" : "bg-gold/10 text-gold-text"}`}>
                    {p.sensitive ? <AlertTriangle className="size-4" /> : p.reserved ? <Lock className="size-4" /> : <KeyRound className="size-4" />}
                  </span>
                  <div className="min-w-0 text-sm">
                    <p className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">{readableCode(p.code)}</span>
                      {p.sensitive && <span className="rounded-full bg-amber-500/10 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-300">Sensitive</span>}
                      {p.reserved && <span className="rounded-full bg-background px-2 py-0.5 text-[11px] font-medium text-muted ring-1 ring-border">Reserved</span>}
                    </p>
                    <p className="mt-0.5 text-xs text-muted">{p.description}</p>
                    <p className="mt-1 font-mono text-[11px] break-all text-muted">{p.code}</p>
                  </div>
                </li>
              ))}
              {items.length % 2 === 1 && <li aria-hidden="true" className="hidden bg-surface md:block" />}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
