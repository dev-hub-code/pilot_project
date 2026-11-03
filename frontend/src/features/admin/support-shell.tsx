import Link from "next/link";
import type { ReactNode } from "react";
import { Permission, hasPermission } from "@/lib/permissions";
import { requireStaff } from "@/lib/server/auth/session";

const P = Permission;

/** The support area's sections; each shows only for the permissions its API needs. */
const TABS = [
  { key: "queue", label: "Tickets", href: "/admin/support", requires: [P.SUPPORT_TICKET_VIEW, P.FINANCE_TICKET_HANDLE] },
  { key: "dashboard", label: "Dashboard", href: "/admin/support/dashboard", requires: [P.SUPPORT_TICKET_VIEW, P.SUPPORT_ANALYTICS_VIEW, P.REPORT_VIEW] },
  { key: "escalations", label: "Escalations", href: "/admin/support/escalations", requires: [P.SUPPORT_TICKET_VIEW, P.SUPPORT_ANALYTICS_VIEW] },
  { key: "feedback", label: "Feedback", href: "/admin/support/feedback", requires: [P.SUPPORT_TICKET_VIEW, P.SUPPORT_ANALYTICS_VIEW, P.REPORT_VIEW] },
  { key: "knowledge-base", label: "Knowledge base", href: "/admin/support/knowledge-base",
    requires: [P.KNOWLEDGE_BASE_MANAGE, P.SYSTEM_SETTINGS_MANAGE, P.SUPPORT_TICKET_VIEW, P.FINANCE_TICKET_HANDLE] },
  { key: "settings", label: "Settings", href: "/admin/support/settings",
    requires: [P.SUPPORT_CONFIG_MANAGE, P.SYSTEM_SETTINGS_MANAGE, P.SUPPORT_TICKET_VIEW] },
] as const;

export type SupportTab = (typeof TABS)[number]["key"];

export async function SupportShell({ active, children }: { active: SupportTab; children: ReactNode }) {
  const session = await requireStaff();
  const can = (perms: readonly string[]) => perms.some((p) => hasPermission(session.permissions, p));
  const tabs = TABS.filter((t) => can(t.requires));
  const reports = hasPermission(session.permissions, P.REPORT_VIEW);
  return (
    <div className="space-y-6">
      {tabs.length > 1 && (
        <nav aria-label="Support" className="-mx-1 flex gap-1 overflow-x-auto border-b border-border pb-px text-sm">
          {tabs.map((t) => (
            <Link key={t.key} href={t.href} aria-current={t.key === active ? "page" : undefined}
              className={`whitespace-nowrap border-b-2 px-3 py-2 ${t.key === active ? "border-gold font-medium text-foreground" : "border-transparent text-muted hover:text-foreground"}`}>
              {t.label}
            </Link>
          ))}
          {reports && (
            <Link href="/admin/reports/ticket-agent-performance" className="whitespace-nowrap border-b-2 border-transparent px-3 py-2 text-muted hover:text-foreground">
              Reports
            </Link>
          )}
        </nav>
      )}
      {children}
    </div>
  );
}

/** Who may do what in the support area (the API enforces the same). */
export function supportAbilities(permissions: readonly string[]) {
  const has = (...perms: string[]) => perms.some((p) => hasPermission(permissions, p));
  return {
    view: has(P.SUPPORT_TICKET_VIEW),
    manage: has(P.SUPPORT_TICKET_MANAGE),
    financeOnly: !has(P.SUPPORT_TICKET_VIEW) && has(P.FINANCE_TICKET_HANDLE),
    escalate: has(P.SUPPORT_TICKET_MANAGE, P.SUPPORT_ESCALATE),
    bulkAssign: has(P.SUPPORT_TICKET_MANAGE, P.SUPPORT_ASSIGN),
    create: has(P.SUPPORT_TICKET_MANAGE, P.SUPPORT_CREATE),
    configure: has(P.SUPPORT_CONFIG_MANAGE, P.SYSTEM_SETTINGS_MANAGE),
    editArticles: has(P.KNOWLEDGE_BASE_MANAGE, P.SYSTEM_SETTINGS_MANAGE),
  };
}
