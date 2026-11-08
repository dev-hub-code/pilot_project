import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import { CalendarDays, Download, FileSignature, History } from "lucide-react";
import { ConfigPanel, ConfigStat } from "@/features/admin/config-ui";
import { AgreementTemplateForm } from "@/features/admin/agreement-form";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { AgreementTemplateView } from "@/types/agreement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Investment agreement" };

export default async function AdminAgreementPage() {
  const denied = await deniedUnlessInMenu("/admin/platform-config/agreement");
  if (denied) return denied;
  await requireStaff();
  const view = await authFetch<AgreementTemplateView>("/api/v1/admin/agreement-template");
  const current = view.current;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2">
        <ConfigStat icon={FileSignature} label="Wording in force" value={current ? `Version ${current.version}` : "No wording yet"} />
        <ConfigStat icon={CalendarDays} label="Saved" value={current ? formatDateTime(current.createdAt) : "—"}
          hint={current ? current.createdByName ?? "As shipped" : undefined} />
      </div>

      <ConfigPanel icon={FileSignature} title="Investment agreement"
        description="The agreement each investor receives when their payment is confirmed. It is filled in with the investment's details and kept as issued: changing the wording here affects only agreements issued afterwards."
        actions={current && (
          <a href="/admin/platform-config/agreement/sample"
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium transition-colors hover:border-gold/50 hover:text-gold-text">
            <Download aria-hidden="true" className="size-4" /> Sample PDF</a>
        )}>
        {!current && <p className="rounded-xl bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-200">Investors cannot be issued an agreement until it has wording.</p>}
        <AgreementTemplateForm title={current?.title ?? "Investment Agreement"} body={current?.body ?? ""}
          placeholders={view.placeholders} />
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
