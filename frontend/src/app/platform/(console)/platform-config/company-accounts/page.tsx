import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import {
  AlertTriangle, Building, CalendarDays, CheckCircle2, Hash, Landmark, type LucideIcon, PauseCircle, Pencil, Plus, Receipt, Smartphone, Wallet,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { ConfigPanel, HowItWorks } from "@/features/admin/config-ui";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { setCompanyBankAccountActiveAction } from "@/features/admin/payment-actions";
import { CompanyBankAccountForm } from "@/features/admin/payment-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { CompanyBankAccount } from "@/types/order";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Company bank accounts" };

export default async function CompanyBankAccountsPage() {
  const denied = await deniedUnlessInMenu("/platform/platform-config/company-accounts");
  if (denied) return denied;
  const session = await requireStaff();
  const canManage = hasPermission(session.permissions, Permission.COMPANY_BANK_ACCOUNT_MANAGE);
  const accounts = await authFetch<CompanyBankAccount[]>("/api/v1/admin/company-bank-accounts");
  const activeCount = accounts.filter((a) => a.active).length;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-3">
        <Tile icon={Landmark} label="Accounts" value={accounts.length} />
        <Tile icon={CheckCircle2} label="Offered to investors" value={activeCount} tone={activeCount === 0 ? "warn" : "good"} />
        <Tile icon={PauseCircle} label="Inactive" value={accounts.length - activeCount} />
      </div>

      {activeCount === 0 && (
        <p role="status" className="flex items-start gap-2 rounded-2xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200">
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          No active account: investors cannot pay by bank until one is added or activated.
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-4">
          {accounts.length === 0 ? (
            <EmptyState title="No bank accounts yet" description={canManage ? "Add the first one below." : undefined} />
          ) : (
            <ul className="grid gap-4 xl:grid-cols-2">
              {accounts.map((a) => (
                <li key={a.id} className={`flex flex-col overflow-hidden rounded-2xl border bg-surface shadow-sm ${a.active ? "border-border" : "border-dashed border-border opacity-80"}`}>
                  <div className={`relative isolate flex items-start justify-between gap-3 overflow-hidden p-5 ${a.active ? "bg-ink text-on-ink" : "bg-background/60"}`}>
                    {a.active && <div aria-hidden="true" className="absolute -top-12 -right-12 -z-10 size-32 rounded-full bg-gold/25 blur-3xl" />}
                    <div className="flex min-w-0 items-center gap-3">
                      <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${a.active ? "bg-gold text-on-gold" : "bg-surface text-muted ring-1 ring-border"}`}>
                        <Landmark className="size-5" />
                      </span>
                      <div className="min-w-0">
                        <p className="font-semibold break-words">{a.bankName}</p>
                        <p className={`text-xs break-words ${a.active ? "text-on-ink/70" : "text-muted"}`}>{a.accountName}{a.branch && ` · ${a.branch}`}</p>
                      </div>
                    </div>
                    <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${a.active
                      ? "bg-emerald-400/20 text-emerald-200" : "bg-surface text-muted ring-1 ring-border"}`}>
                      <span aria-hidden="true" className={`size-1.5 rounded-full ${a.active ? "bg-emerald-400" : "bg-muted"}`} />
                      {a.active ? "Active" : "Inactive"}
                    </span>
                  </div>
                  <dl className="grid gap-px bg-border sm:grid-cols-2">
                    <Item icon={Hash} label="Account number"><span className="font-mono">{a.accountNumber}</span></Item>
                    <Item icon={Building} label="IFSC"><span className="font-mono">{a.ifscCode}</span></Item>
                    <Item icon={Smartphone} label="UPI ID">{a.upiId ?? <span className="text-muted">None</span>}</Item>
                    <Item icon={CalendarDays} label="Added">{formatDateTime(a.createdAt)}</Item>
                  </dl>
                  {canManage && (
                    <div className="mt-auto flex flex-wrap items-start gap-3 border-t border-border p-4">
                      <details className="group min-w-0 flex-1 basis-full">
                        <summary className="inline-flex cursor-pointer list-none items-center gap-1.5 text-sm font-medium text-gold-text marker:hidden">
                          <Pencil aria-hidden="true" className="size-4" /> <span className="group-open:hidden">Edit details</span><span className="hidden group-open:inline">Close editing</span>
                        </summary>
                        <div className="pt-4"><CompanyBankAccountForm account={a} /></div>
                      </details>
                      <ConfirmForm action={setCompanyBankAccountActiveAction.bind(null, a.id, !a.active)}
                        submitLabel={a.active ? "Deactivate" : "Activate"} variant={a.active ? "secondary" : "primary"}
                        confirm={a.active
                          ? `Stop offering ${a.bankName} ${a.accountNumber} to investors? Payments already made into it are unaffected.`
                          : `Offer ${a.bankName} ${a.accountNumber} to investors again?`} />
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}

          {canManage && (
            <ConfigPanel icon={Plus} title="Add an account" description="Investors see these details exactly as entered.">
              <CompanyBankAccountForm />
            </ConfigPanel>
          )}
        </div>

        <HowItWorks points={[
          { icon: Wallet, text: "These are the accounts investors pay into." },
          { icon: CheckCircle2, text: "Investors choosing bank payment pick one of the active accounts." },
          { icon: Receipt, text: "They then give their transaction ID or cheque number." },
          { icon: PauseCircle, text: "Deactivating stops offering an account; payments already made into it are unaffected." },
        ]} />
      </div>
    </div>
  );
}

function Tile({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: number; tone?: "good" | "warn" }) {
  const color = tone === "warn" ? "bg-amber-500/10 text-amber-700 dark:text-amber-300"
    : tone === "good" ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300" : "bg-gold/10 text-gold-text";
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
      <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${color}`}><Icon className="size-5" /></span>
      <span>
        <span className="block text-2xl font-semibold tracking-tight tabular-nums">{value}</span>
        <span className="text-xs text-muted">{label}</span>
      </span>
    </div>
  );
}

function Item({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2.5 bg-surface px-5 py-3">
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted" />
      <div className="min-w-0">
        <dt className="text-[11px] text-muted">{label}</dt>
        <dd className="text-sm font-medium break-all">{children}</dd>
      </div>
    </div>
  );
}
