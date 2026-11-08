import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { CalendarDays, ExternalLink, FileText, History, UsersRound } from "lucide-react";
import { ConfigPanel, ConfigStat } from "@/features/admin/config-ui";
import { TermsForm } from "@/features/admin/terms-form";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { TermsView } from "@/types/terms";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Terms & conditions" };

export default async function AdminTermsPage() {
  const denied = await deniedUnlessInMenu("/admin/platform-config/terms");
  if (denied) return denied;
  await requireStaff();
  const view = await authFetch<TermsView>("/api/v1/admin/terms");
  const current = view.current;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-3">
        <ConfigStat icon={FileText} label="In force" value={current ? `Version ${current.version}` : "No terms yet"} />
        <ConfigStat icon={CalendarDays} label="Saved" value={current ? formatDateTime(current.createdAt) : "—"}
          hint={current ? current.createdByName ?? "As shipped" : undefined} />
        <ConfigStat icon={UsersRound} label="Accepted by" value={`${view.accepted} account${view.accepted === 1 ? "" : "s"}`} />
      </div>

      <ConfigPanel icon={FileText} title={current ? "Edit the terms" : "Write the terms"}
        description="The terms everyone accepts when they create an account, shown on the public Terms & conditions page. Saving makes a new version; each account keeps the version it accepted."
        actions={<Link href="/terms" target="_blank"
          className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium transition-colors hover:border-gold/50 hover:text-gold-text">
          <ExternalLink aria-hidden="true" className="size-4" /> View page</Link>}>
        {!current && <p className="rounded-xl bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-200">No one can sign up until the terms have wording.</p>}
        <TermsForm title={current?.title ?? "Terms and Conditions"} body={current?.body ?? ""} />
      </ConfigPanel>

      {view.history.length > 1 && (
        <section aria-labelledby="versions-title" className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          <h2 id="versions-title" className="flex items-center gap-3 border-b border-border bg-background/40 px-5 py-4 font-semibold tracking-tight">
            <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
              <History className="size-[18px]" />
            </span>
            Earlier versions <span className="text-sm font-normal text-muted">{view.history.length - 1}</span>
          </h2>
          <ol className="divide-y divide-border">
            {view.history.slice(1).map((t) => (
              <li key={t.id} className="flex items-center gap-3 px-5 py-3 text-sm opacity-80">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-background text-xs font-semibold tabular-nums ring-1 ring-border">v{t.version}</span>
                <div className="min-w-0 flex-1">
                  <p className="font-medium break-words">{t.title}</p>
                  <p className="text-xs text-muted">{formatDateTime(t.createdAt)} · {t.createdByName ?? "As shipped"}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}
