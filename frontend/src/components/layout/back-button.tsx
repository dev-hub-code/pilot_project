"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useSyncExternalStore } from "react";

/**
 * The app's navigation stack: the in-app pages visited in this tab (path and query), newest last, kept in
 * sessionStorage so it survives reloads but not a new tab. Going back (the button or the browser's) pops it.
 */
const KEY = "sealease:nav-stack";
const MAX = 50;
const listeners = new Set<() => void>();

function read(): string[] {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(KEY) ?? "[]");
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
  } catch {
    return [];
  }
}

function write(stack: string[]) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(stack.slice(-MAX)));
  } catch {
    // Storage unavailable (private mode): the button falls back to the page's parent.
  }
  listeners.forEach((listener) => listener());
}

/** Records a page visit: the page before the current one means the user went back; anything else is a new step. */
function visit(entry: string) {
  const stack = read();
  if (stack.at(-1) === entry) return;
  if (stack.at(-2) === entry) write(stack.slice(0, -1));
  else write([...stack, entry]);
}

/** The page the user came from, or null when this tab has none (opened directly, from a bookmark or an email). */
function usePrevious(): string | null {
  const snapshot = useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => read().at(-2) ?? "",
    () => "",
  );
  return snapshot || null;
}

/** Every page of the app, to find the nearest one above a path ("[id]" matches one segment). */
const PAGES = [
  "/dashboard", "/marketplace", "/marketplace/[id]", "/cart", "/orders", "/orders/[id]", "/orders/[id]/invoice",
  "/portfolio", "/earnings", "/statements", "/referrals", "/withdrawals", "/support", "/support/new", "/support/[id]",
  "/notifications", "/profile", "/profile/bank-accounts", "/profile/verification", "/settings",
  "/settings/notifications", "/settings/password",
  "/admin", "/admin/agreement", "/admin/audit", "/admin/audit/[id]", "/admin/bank-accounts",
  "/admin/company-bank-accounts", "/admin/container-assignments", "/admin/finance-tasks", "/admin/kyc",
  "/admin/kyc/[id]", "/admin/leads", "/admin/leads/new", "/admin/leads/[id]", "/admin/ledger", "/admin/ledger/[id]",
  "/admin/orders", "/admin/orders/[id]", "/admin/orders/[id]/invoice", "/admin/payments", "/admin/payouts",
  "/admin/products", "/admin/products/new", "/admin/products/[id]", "/admin/products/[id]/edit", "/admin/referrals",
  "/admin/reports", "/admin/roles", "/admin/roles/[id]", "/admin/security", "/admin/support", "/admin/support/[id]",
  "/admin/users", "/admin/users/new", "/admin/users/[id]", "/admin/withdrawal-runs", "/admin/withdrawals",
  "/admin/withdrawals/batches", "/admin/withdrawals/batches/[id]", "/admin/withdrawals/[id]",
].map((page) => new RegExp(`^${page.replaceAll("[id]", "[^/]+")}$`));

/** The nearest page above a path: "/portfolio/1/agreement" → "/portfolio", "/support/1" → "/support". */
function parentOf(path: string, home: string): string {
  let parent = path;
  while (parent.includes("/", 1)) {
    parent = parent.slice(0, parent.lastIndexOf("/"));
    if (PAGES.some((page) => page.test(parent))) return parent;
  }
  return home;
}

/** What a page is called on the button ("Back to Orders"), by its path; null for a generic "Back". */
const NAMES: [RegExp, string][] = [
  [/^\/dashboard$/, "Dashboard"], [/^\/admin$/, "Dashboard"],
  [/^\/marketplace\/[^/]+$/, "container"], [/^\/orders\/[^/]+\/invoice$/, "invoice"], [/^\/orders\/[^/]+$/, "order"],
  [/^\/support\/new$/, "new ticket"], [/^\/support\/[^/]+$/, "ticket"], [/^\/portfolio\/[^/]+\/agreement$/, "agreement"],
  [/^\/referrals\/levels\/([^/]+)$/, "level $1"], [/^\/referrals\/members\/[^/]+$/, "member"],
  [/^\/profile\/bank-accounts$/, "Bank accounts"], [/^\/profile\/verification$/, "Verification"],
  [/^\/settings\/notifications$/, "Notification settings"], [/^\/settings\/password$/, "Password"],
  [/^\/admin\/orders\/[^/]+\/invoice$/, "invoice"], [/^\/admin\/products\/[^/]+\/edit$/, "edit product"],
  [/^\/admin\/withdrawals\/batches\/[^/]+$/, "withdrawal file"], [/^\/admin\/withdrawals\/batches$/, "Withdrawal files"],
  [/^\/admin\/users\/new$/, "new user"], [/^\/admin\/leads\/new$/, "new lead"], [/^\/admin\/products\/new$/, "new product"],
  [/^\/admin\/users\/[^/]+$/, "user"], [/^\/admin\/kyc\/[^/]+$/, "KYC submission"], [/^\/admin\/leads\/[^/]+$/, "lead"],
  [/^\/admin\/ledger\/[^/]+$/, "ledger account"], [/^\/admin\/orders\/[^/]+$/, "order"],
  [/^\/admin\/products\/[^/]+$/, "product"], [/^\/admin\/roles\/[^/]+$/, "role"], [/^\/admin\/support\/[^/]+$/, "ticket"],
  [/^\/admin\/withdrawals\/[^/]+$/, "withdrawal"], [/^\/admin\/audit\/[^/]+$/, "audit entry"],
  [/^\/admin\/kyc$/, "KYC"],
];
const words = (segment: string) => segment.replaceAll("-", " ").replace(/^./, (c) => c.toUpperCase());

function nameOf(entry: string): string {
  const path = entry.split("?")[0] ?? entry;
  for (const [pattern, name] of NAMES) {
    if (pattern.test(path)) return path.replace(pattern, name);
  }
  // Top-level pages: "/orders" → "Orders", "/admin/finance-tasks" → "Finance tasks".
  const top = /^(?:\/admin)?\/([^/]+)$/.exec(path);
  return top?.[1] ? words(top[1]) : "previous page";
}

function BackButtonInner({ home }: { home: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const query = useSearchParams().toString();
  const entry = query ? `${pathname}?${query}` : pathname;
  const previous = usePrevious();

  useEffect(() => visit(entry), [entry]);

  if (pathname === home) return null;
  const target = previous ?? parentOf(pathname, home);
  return (
    <button type="button"
      onClick={() => (previous ? router.back() : router.push(target))}
      className="-my-1.5 inline-flex min-h-11 items-center gap-1.5 text-sm text-muted transition-colors hover:text-foreground focus-visible:text-foreground">
      <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.8"
        strokeLinecap="round" strokeLinejoin="round">
        <path d="M12.5 4.5 7 10l5.5 5.5" />
      </svg>
      Back to {nameOf(target)}
    </button>
  );
}

/**
 * "← Back to …" above every page but the home page: returns to the page the user came from in this tab (the browser's
 * history, so scroll and forward still work), or, when they arrived directly, to the page's parent.
 */
export function BackButton({ home }: { home: string }) {
  return (
    <div className="mx-auto w-full max-w-7xl px-4 pt-5 sm:px-6 print:hidden">
      <Suspense>
        <BackButtonInner home={home} />
      </Suspense>
    </div>
  );
}
