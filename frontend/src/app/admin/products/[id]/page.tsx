import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import {
  cancelProductAction, closeProductAction, publishProductAction, removePlanPhotoAction, uploadPlanPhotosAction,
} from "@/features/admin/investment-actions";
import { PlanPhotoForm } from "@/features/admin/plan-photo-form";
import { MAX_PLAN_PHOTOS as MAX_PHOTOS } from "@/features/admin/plan-photos";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { CONTAINER_TYPE_LABEL, PRODUCT_STATUS_LABEL, PRODUCT_TONE } from "@/features/marketplace/labels";
import { yearsLabel } from "@/features/orders/lease-years";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { PlanPhoto, Product } from "@/types/marketplace";
import { formatDateTime } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Plan" };


export default async function ProductPage({ params, searchParams }: PageProps<"/admin/products/[id]">) {
  const { id } = await params;
  const photosFailed = Number((await searchParams).photosFailed ?? 0);
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
  const canEdit = can(Permission.INVESTMENT_UPDATE) && p.status !== "CANCELLED";
  const photos = await authFetch<PlanPhoto[]>(`/api/v1/admin/investment-products/${id}/photos`);

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
              <Item label="Published">{formatDateTime(p.publishedAt)}</Item>
            </dl>
          </Card>
          {photosFailed > 0 && (
            <Notice tone="warning">
              The plan was saved, but {photosFailed === 1 ? "1 photo" : `${photosFailed} photos`} could not be uploaded. Add
              {photosFailed === 1 ? " it" : " them"} again below.
            </Notice>
          )}
          <Card title="Photos" description={`Shown in the marketplace, the first as the cover. Up to ${MAX_PHOTOS} photos.`}>
            {photos.length === 0 ? (
              <p className="text-sm text-muted">No photos yet: the marketplace shows this plan without a picture.</p>
            ) : (
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {photos.map((photo, index) => (
                  <li key={photo.id} className="space-y-2">
                    <a href={`/admin/products/${p.id}/photos/${photo.documentId}`} target="_blank" rel="noopener noreferrer"
                      className="block aspect-[4/3] overflow-hidden border border-border bg-ink">
                      {/* eslint-disable-next-line @next/next/no-img-element -- authenticated document, not an optimisable asset */}
                      <img src={`/admin/products/${p.id}/photos/${photo.documentId}`} alt={`Photo ${index + 1} of ${p.code}`}
                        className="size-full object-cover" />
                    </a>
                    {index === 0 && <p className="text-xs text-muted">Cover</p>}
                    {canEdit && (
                      <ConfirmForm action={removePlanPhotoAction.bind(null, p.id, photo.id)} submitLabel="Remove"
                        confirm="Remove this photo from the plan?" />
                    )}
                  </li>
                ))}
              </ul>
            )}
            {canEdit && photos.length < MAX_PHOTOS && (
              <div className="mt-4 border-t border-border pt-4">
                <PlanPhotoForm action={uploadPlanPhotosAction.bind(null, p.id)} room={MAX_PHOTOS - photos.length} />
              </div>
            )}
          </Card>
          <Card title="Payout per container" description="Credited to the investor's wallet every hour for the lease they choose; part months at the start and end are paid by the hour.">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Price (without GST)">{formatMoney(p.price)}</Item>
              <Item label={`GST (${formatPercent(p.gstRatePercent)})`}>{formatMoney(p.gstPerContainer)}</Item>
              <Item label="Investor pays">{formatMoney(p.priceWithGst)}</Item>
              <Item label={`Rent (${formatPercent(p.monthlyRentPercent)} a month)`}>{formatMoney(p.monthlyRent)}</Item>
              <Item label="Lease (investor's choice)">{p.minTenureYears === p.maxTenureYears ? yearsLabel(p.minTenureYears)
                : `${p.minTenureYears} to ${p.maxTenureYears} years`}</Item>
              <Item label={`Total rent over ${yearsLabel(p.minTenureYears)} (the shortest)`}>{formatMoney(p.totalPayout)}</Item>
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
              <Card title="Close to new investors" description="Containers already sold keep their lease and keep earning rent every hour.">
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
