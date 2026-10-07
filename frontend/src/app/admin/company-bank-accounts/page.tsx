import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { setCompanyBankAccountActiveAction } from "@/features/admin/payment-actions";
import { CompanyBankAccountForm } from "@/features/admin/payment-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { CompanyBankAccount } from "@/types/order";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Company bank accounts" };

export default async function CompanyBankAccountsPage() {
  const session = await requireStaff();
  const canManage = hasPermission(session.permissions, Permission.COMPANY_BANK_ACCOUNT_MANAGE);
  const accounts = await authFetch<CompanyBankAccount[]>("/api/v1/admin/company-bank-accounts");
  const activeCount = accounts.filter((a) => a.active).length;

  return (
    <div className="space-y-6">
      <PageHeader title="Company bank accounts"
        description="The accounts investors pay into. Investors choosing bank payment pick one of the active accounts, then give their transaction ID, cheque number or deposit receipt number." />

      {activeCount === 0 && (
        <Notice tone="warning">No active account: investors cannot pay by bank until one is added or activated.</Notice>
      )}

      {accounts.length === 0 ? (
        <EmptyState title="No bank accounts yet" />
      ) : (
        <ul className="space-y-4">
          {accounts.map((a) => (
            <li key={a.id} className="space-y-4 border border-border bg-surface p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-semibold">{a.bankName}{a.branch && <span className="font-normal text-muted"> · {a.branch}</span>}</p>
                  <p className="text-sm text-muted">{a.accountName}</p>
                </div>
                <StatusBadge tone={a.active ? "success" : "neutral"}>{a.active ? "Active" : "Inactive"}</StatusBadge>
              </div>
              <dl className="grid gap-3 text-sm sm:grid-cols-4">
                <Item label="Account number"><span className="font-mono">{a.accountNumber}</span></Item>
                <Item label="IFSC"><span className="font-mono">{a.ifscCode}</span></Item>
                <Item label="UPI ID">{a.upiId ?? "—"}</Item>
                <Item label="Added">{formatDateTime(a.createdAt)}</Item>
              </dl>
              {canManage && (
                <div className="flex flex-wrap items-start gap-4 border-t border-border pt-4">
                  <details className="min-w-0 flex-1">
                    <summary className="cursor-pointer text-sm text-gold-text">Edit details</summary>
                    <div className="pt-4"><CompanyBankAccountForm account={a} /></div>
                  </details>
                  <ConfirmForm action={setCompanyBankAccountActiveAction.bind(null, a.id, !a.active)}
                    submitLabel={a.active ? "Deactivate" : "Activate"}
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
        <Card title="Add an account" description="Investors see these details exactly as entered.">
          <CompanyBankAccountForm />
        </Card>
      )}
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
