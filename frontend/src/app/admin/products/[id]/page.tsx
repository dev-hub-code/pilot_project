import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import { cancelProductAction, closeProductAction, publishProductAction } from "@/features/admin/investment-actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { CONTAINER_TYPE_LABEL, PRODUCT_STATUS_LABEL, PRODUCT_TONE } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Product } from "@/types/marketplace";
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
          {p.status !== "DRAFT" && p.status !== "CANCELLED" && (
            <LinkButton href={`/marketplace/${p.id}`} variant="secondary">View as investor</LinkButton>
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
              <Item label="Containers sold">{p.containersSold}</Item>
              <Item label={`${CONTAINER_TYPE_LABEL[p.containerType]} in stock`}>
                <Link href={`/admin/containers?containerType=${p.containerType}&status=AVAILABLE`} className="text-gold-text hover:underline">
                  {p.availableContainers}
                </Link>
              </Item>
              <Item label="Published">{formatDateTime(p.publishedAt)}</Item>
            </dl>
            {p.status === "OPEN" && p.availableContainers === 0 && (
              <p className="mt-4 text-sm text-muted">No {CONTAINER_TYPE_LABEL[p.containerType]} containers are in stock: investors cannot buy until some are registered.</p>
            )}
          </Card>
          <Card title="Payout per container" description={`Paid every month for ${p.tenureMonths} months, starting one month after payment.`}>
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Price">{formatMoney(p.price)}</Item>
              <Item label={`Rent (${formatPercent(p.monthlyRentPercent)} a month)`}>{formatMoney(p.monthlyRent)}</Item>
              <Item label={`Capital back (${formatPercent(p.monthlyCapitalReturnPercent)} a month)`}>{formatMoney(p.monthlyCapitalReturn)}</Item>
              <Item label="Monthly payout">{formatMoney(p.monthlyPayout)}</Item>
              <Item label="Payouts">{p.tenureMonths}</Item>
              <Item label="Total over the tenure">{formatMoney(p.totalPayout)}</Item>
            </dl>
          </Card>
          <Card title="Terms">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Container type">{CONTAINER_TYPE_LABEL[p.containerType]}</Item>
              {p.closedAt && <Item label="Closed">{formatDateTime(p.closedAt)}</Item>}
              {p.cancellationReason && <Item label="Cancelled">{p.cancellationReason}</Item>}
            </dl>
          </Card>
        </div>

        {decisions && (
          <div className="space-y-6">
            {p.status === "DRAFT" && (
              <Card title="Publish" description="Terms become binding and investors can buy containers under the plan.">
                <ConfirmForm action={publishProductAction.bind(null, p.id)} submitLabel="Publish plan"
                  confirm={`Publish ${p.code}? Its terms can no longer be edited.`} />
              </Card>
            )}
            {p.status === "OPEN" && (
              <Card title="Close to new investors" description="Containers already sold keep their lease and monthly payouts.">
                <ConfirmForm action={closeProductAction.bind(null, p.id)} submitLabel="Close plan"
                  confirm={`Close ${p.code}? Investors can no longer buy containers under it.`} />
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
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}
