import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import {
  cancelProductAction, closeProductAction, publishProductAction, reopenProductAction,
} from "@/features/admin/investment-actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { AUDIENCE_LABEL, PRODUCT_STATUS_LABEL, PRODUCT_TONE } from "@/features/plans/labels";
import { yearsLabel } from "@/features/orders/lease-years";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Product } from "@/types/plan";
import { formatDateTime } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Plan" };


export default async function ProductPage({ params }: PageProps<"/admin/products/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let p: Product;
  try {
    p = await authFetch<Product>(`/api/v1/admin/investment-products/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const credit = creditLabel(await getRentCredit());
  const decisions = can(Permission.INVESTMENT_APPROVE) && (p.status === "DRAFT" || p.status === "OPEN");

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <p className="font-mono text-sm text-muted">{p.code}</p>
          <h1 className="text-3xl font-semibold tracking-tight">{p.title}</h1>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge tone={PRODUCT_TONE[p.status]}>{PRODUCT_STATUS_LABEL[p.status]}</StatusBadge>
          {p.audience === "ONLINE" && p.status !== "DRAFT" && p.status !== "CANCELLED" && (
            <LinkButton href={`/invest/${p.id}`} variant="secondary">View as investor</LinkButton>
          )}
          {p.status === "DRAFT" && can(Permission.INVESTMENT_UPDATE) && (
            <LinkButton href={`/admin/products/${p.id}/edit`} variant="secondary">Edit draft</LinkButton>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <Card title="Sales">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Investments">{p.containersSold}</Item>
              <Item label="Published">{formatDateTime(p.publishedAt)}</Item>
            </dl>
          </Card>
          <Card title="Payout" description={`Returns are credited to the investor's wallet ${credit}.`}>
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Offered to">{AUDIENCE_LABEL[p.audience]}</Item>
              <Item label="Tenure">{yearsLabel(p.tenureYears)}</Item>
              <Item label="Return a year">{formatPercent(p.annualReturnPercent)}</Item>
              <Item label="Price of 1 unit">{formatMoney({ amount: String(p.unitPrice), currency: p.currency })}</Item>
              <Item label="Minimum units">{p.minUnits}</Item>
            </dl>
          </Card>
          {(p.closedAt || p.cancellationReason) && (
            <Card title="Terms">
              <dl className="grid gap-4 text-sm sm:grid-cols-3">
                {p.closedAt && <Item label="Closed">{formatDateTime(p.closedAt)}</Item>}
                {p.cancellationReason && <Item label="Cancelled">{p.cancellationReason}</Item>}
              </dl>
            </Card>
          )}
        </div>

        {decisions && (
          <div className="space-y-6">
            {p.status === "DRAFT" && (
              <Card title="Publish" description="Terms become binding and investors can invest in this plan.">
                <ConfirmForm action={publishProductAction.bind(null, p.id)} submitLabel="Publish plan"
                  confirm={`Publish ${p.code}? Its terms can no longer be edited.`} />
              </Card>
            )}
            {p.status === "OPEN" && (
              <Card title="Close to new investors" description="Investments already made keep their term and keep earning returns every hour.">
                <ConfirmForm action={closeProductAction.bind(null, p.id)} submitLabel="Close plan"
                  confirm={`Close ${p.code}? Investors can no longer invest in it.`} />
              </Card>
            )}
            {p.status === "CLOSED" && (
              <Card title="Reopen to new investors" description="Investors can invest in this plan again, on the same terms.">
                <ConfirmForm action={reopenProductAction.bind(null, p.id)} submitLabel="Reopen plan"
                  confirm={`Reopen ${p.code}? Investors can invest in it again.`} />
              </Card>
            )}
            {p.status === "DRAFT" && (
              <Card title="Cancel draft">
                <ReasonForm action={cancelProductAction.bind(null, p.id)} label="Reason" submitLabel="Cancel plan"
                  confirm={`Cancel ${p.code}?`} />
              </Card>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}
