import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { LinkButton } from "@/components/ui/link-button";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge } from "@/components/ui/status-badge";
import { cancelProductAction, publishProductAction } from "@/features/admin/investment-actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { ActivateLeaseForm } from "@/features/admin/rental-forms";
import { todayUtc } from "@/features/earnings/labels";
import { CONTAINER_TYPE_LABEL, PRODUCT_TONE, RISK_LABEL } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { CapacityMovement, Product } from "@/types/marketplace";
import { formatDate, formatDateTime, humanize } from "@/utils/format";
import { formatMoney, formatPercent, FREQUENCY_LABEL } from "@/utils/money";

export const metadata: Metadata = { title: "Offering" };

export default async function ProductPage({ params }: PageProps<"/admin/products/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let product: Product;
  try {
    product = await authFetch<Product>(`/api/v1/admin/investment-products/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const movements = await authFetch<CapacityMovement[]>(`/api/v1/admin/investment-products/${id}/capacity-movements`);
  const p = product;
  const reserved = Number(p.capacity.reserved.amount);
  const committed = Number(p.capacity.committed.amount);
  const cancellable = (p.status === "DRAFT" || p.status === "OPEN") && reserved === 0 && committed === 0;
  // Mirrors the backend rule: funded, or open with confirmed investors and nothing awaiting payment.
  const leasable = p.status === "FUNDED" || (p.status === "OPEN" && committed > 0 && reserved === 0);
  const onLease = p.status === "ACTIVE" || p.status === "MATURED" || p.status === "CLOSED";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <p className="font-mono text-sm text-muted">{p.code}</p>
          <h1 className="text-3xl font-semibold tracking-tight">{p.title}</h1>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge tone={PRODUCT_TONE[p.status]}>{humanize(p.status)}</StatusBadge>
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
          <Card title="Funding">
            <ProgressBar percent={p.capacity.fundedPercent} label="Funding progress" />
            <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-4">
              <Item label="Price">{formatMoney(p.capacity.total)}</Item>
              <Item label="Committed">{formatMoney(p.capacity.committed)}</Item>
              <Item label="Reserved">{formatMoney(p.capacity.reserved)}</Item>
              <Item label="Available">{formatMoney(p.capacity.available)}</Item>
            </dl>
          </Card>
          {onLease && (
            <Card title="Lease" actions={
              <Link href={`/admin/rentals?view=DISTRIBUTED`} className="text-sm text-gold-text hover:underline">Rental payments</Link>
            }>
              <dl className="grid gap-4 text-sm sm:grid-cols-3">
                <Item label="Started">{formatDate(p.leaseStartsOn)}</Item>
                <Item label="Ends">{formatDate(p.leaseEndsOn)}</Item>
                <Item label="Matured">{formatDateTime(p.maturedAt)}</Item>
              </dl>
            </Card>
          )}
          <Card title="Terms">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Type">{p.investmentType === "HNI" ? "Standalone (HNI)" : "Shared (retail)"}</Item>
              <Item label="Minimum / step">{formatMoney(p.minimumInvestment)} / {formatMoney(p.investmentIncrement)}</Item>
              <Item label="Max per investor">{formatMoney(p.maximumPerInvestor)}</Item>
              <Item label="Rental">{formatMoney(p.expectedRentalAmount)}/{FREQUENCY_LABEL[p.rentalFrequency]}</Item>
              <Item label="Management fee">{formatPercent(p.managementFeePercent)} of rental</Item>
              <Item label="Expected yield (net)">{formatPercent(p.expectedAnnualReturnPercent)}</Item>
              <Item label="Term">{p.durationMonths} months</Item>
              <Item label="Risk">{RISK_LABEL[p.riskLevel]}</Item>
              <Item label="Offer window">{formatDateTime(p.offerOpensAt)} → {formatDateTime(p.offerClosesAt)}</Item>
              <Item label="Terms version">{p.termsVersion}</Item>
              <Item label="Container">
                <Link className="font-mono text-gold-text hover:underline" href={`/admin/containers/${p.container.id}`}>{p.container.containerNumber}</Link>
                {" "}· {CONTAINER_TYPE_LABEL[p.container.containerType]}
              </Item>
              <Item label="Published">{formatDateTime(p.publishedAt)}</Item>
              {p.cancellationReason && <Item label="Cancelled">{p.cancellationReason}</Item>}
            </dl>
          </Card>
          <section className="space-y-3">
            <h2 className="text-lg font-semibold tracking-tight">Capacity ledger</h2>
            {movements.length === 0 ? (
              <p className="text-sm text-muted">No reservations yet.</p>
            ) : (
              <DataTable columns={["When", "Movement", "Amount", "Reference", "Reserved after", "Committed after"]}>
                {movements.map((m) => (
                  <tr key={m.id}>
                    <Cell className="text-muted">{formatDateTime(m.createdAt)}</Cell>
                    <Cell>{m.type}</Cell>
                    <Cell className="tabular-nums">{m.amount.toFixed(2)}</Cell>
                    <Cell className="font-mono text-xs">{m.reference}</Cell>
                    <Cell className="tabular-nums">{m.reservedAfter.toFixed(2)}</Cell>
                    <Cell className="tabular-nums">{m.committedAfter.toFixed(2)}</Cell>
                  </tr>
                ))}
              </DataTable>
            )}
          </section>
        </div>

        {can(Permission.INVESTMENT_APPROVE) && (p.status === "DRAFT" || cancellable || leasable) && (
          <div className="space-y-6">
            {leasable && (
              <Card title="Start the lease" description={p.status === "OPEN"
                ? "Not fully funded: starting now closes the offering, and rent on the unsold share stays with the platform."
                : "Rental periods are counted from this date; rent is collected under Rentals."}>
                <ActivateLeaseForm productId={p.id} code={p.code} defaultDate={todayUtc()} />
              </Card>
            )}
            {p.status === "DRAFT" && (
              <Card title="Publish" description="Terms become binding and the offering appears in the marketplace.">
                <ConfirmForm action={publishProductAction.bind(null, p.id)} submitLabel="Publish offering"
                  confirm={`Publish ${p.code}? Its terms can no longer be edited.`} />
              </Card>
            )}
            {cancellable && (
              <Card title="Cancel offering">
                <ReasonForm action={cancelProductAction.bind(null, p.id)} label="Reason" submitLabel="Cancel offering"
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
