import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { SelectField } from "@/components/ui/select-field";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
import { CONTAINER_TYPE_LABEL, PRODUCT_STATUS_LABEL } from "@/features/marketplace/labels";
import { PhotoSlider } from "@/features/marketplace/photo-slider";
import { AddToCartForm } from "@/features/orders/add-to-cart-form";
import { leaseOptions, yearsLabel } from "@/features/orders/lease-years";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { MarketplaceDetail, ReturnProjection } from "@/types/marketplace";
import type { Cart } from "@/types/order";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Plan" };

const CONTAINERS = /^\d{1,2}$/;

export default async function OfferingPage({ params, searchParams }: PageProps<"/marketplace/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const query = await searchParams;
  const session = await requireSession();
  const investor = hasPermission(session.permissions, Permission.INVESTOR_PORTAL);

  let detail: MarketplaceDetail;
  try {
    detail = await authFetch<MarketplaceDetail>(`/api/v1/marketplace/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { listing } = detail;
  const [cart] = await Promise.all([investor ? authFetch<Cart>("/api/v1/cart") : Promise.resolve(null)]);
  const inCart = cart?.items.find((line) => line.productId === id);
  const requested = typeof query.containers === "string" && CONTAINERS.test(query.containers) && Number(query.containers) >= 1
    ? Number(query.containers) : inCart?.quantity ?? 1;
  // The lease the investor is looking at: from the calculator, else the cart, else the plan's shortest.
  const asked = typeof query.years === "string" && CONTAINERS.test(query.years) ? Number(query.years) : null;
  const years = asked !== null && asked >= listing.minTenureYears && asked <= listing.maxTenureYears
    ? asked : inCart?.tenureYears ?? listing.minTenureYears;
  const [projection] = await Promise.all([
    authFetch<ReturnProjection>(`/api/v1/marketplace/${id}/projection?containers=${requested}&years=${years}`),
  ]);
  const leaseRange = listing.minTenureYears === listing.maxTenureYears
    ? yearsLabel(listing.minTenureYears)
    : `${yearsLabel(listing.minTenureYears)} minimum, up to ${yearsLabel(listing.maxTenureYears)}`;

  const photos = detail.photoIds;
  const docHref = (documentId: string) => `/marketplace/${id}/documents/${documentId}`;
  const type = CONTAINER_TYPE_LABEL[listing.containerType];

  return (
    <div className="space-y-10">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/marketplace" className="hover:text-foreground">Marketplace</Link> / <span className="font-mono">{listing.code}</span>
      </nav>

      <header className="grid gap-8 lg:grid-cols-[1.4fr_1fr] [&>*]:min-w-0">
        <div className="space-y-3">
          {photos.length > 0 ? (
            <PhotoSlider photos={photos.map((photo, i) => ({ src: docHref(photo), alt: `A ${type} container, photo ${i + 1}` }))} />
          ) : (
            <div className="relative aspect-[16/9] overflow-hidden bg-ink">
              <ContainerScene focus="side" className="size-full" />
            </div>
          )}
          <p className="text-xs text-muted">Photos show containers of this type; the container assigned to you may differ.</p>
        </div>

        <div className="space-y-6">
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge tone="neutral">{type}</StatusBadge>
              {listing.status !== "OPEN" && <StatusBadge tone="neutral">{PRODUCT_STATUS_LABEL[listing.status]}</StatusBadge>}
            </div>
            <p className="font-mono text-sm text-muted">{listing.code}</p>
            <h1 className="text-3xl leading-tight font-bold tracking-tight sm:text-4xl">{listing.title}</h1>
            <p className="text-muted">{listing.summary}</p>
          </div>

          <dl className="grid grid-cols-2 gap-px border border-border bg-border">
            <Fact label="Rent paid every month" value={formatPercent(listing.monthlyRentPercent)} emphasis />
            <Fact label="Price per container"
              value={Number(listing.gstRatePercent) > 0
                ? `${formatMoney(listing.price)} + ${formatPercent(listing.gstRatePercent)} GST = ${formatMoney(listing.priceWithGst)}`
                : formatMoney(listing.price)} />
            <Fact label="Rent per container a month"
              value={`${formatMoney(detail.monthlyRent)} (${formatPercent(listing.monthlyRentPercent)} of ${formatMoney(listing.price)}${Number(listing.gstRatePercent) > 0 ? ", before GST" : ""})`} />
            <Fact label="Minimum lease tenure"
              value={listing.maxTenureYears > listing.minTenureYears
                ? `${yearsLabel(listing.minTenureYears)} (choose up to ${yearsLabel(listing.maxTenureYears)})`
                : yearsLabel(listing.minTenureYears)} />
          </dl>

          <div className="space-y-1 text-sm">
            <p className="text-muted">
              Buy as many {type} containers as you like. Once your payment is confirmed, our team assigns each one to you
              by its container number; your lease starts at the next full hour and earns rent every hour from then.
            </p>
          </div>
        </div>
      </header>

      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr] [&>*]:min-w-0">
        <div className="space-y-8">
          <Card title="About this plan">
            <div className="space-y-4 text-sm leading-relaxed whitespace-pre-line wrap-anywhere">{detail.description}</div>
          </Card>

          <Card title="How it works">
            <ol className="list-decimal space-y-2 pl-5 text-sm leading-relaxed">
              <li>Choose how many containers to buy at {formatMoney(listing.price)} each{Number(listing.gstRatePercent) > 0 ? ` plus ${formatPercent(listing.gstRatePercent)} GST` : ""}, and check out. Rent is on the price without GST.</li>
              <li>Pay by bank within 30 minutes. Online transfer, cheque or cash deposit are all accepted.</li>
              <li>Choose your lease: whole years, from {leaseRange}.</li>
              <li>Once your payment is confirmed, a {type} container is assigned to you by its unique container number and the lease you chose starts.</li>
              <li>Every month of your lease you receive {formatMoney(detail.monthlyRent)} rent per container in your wallet. You can withdraw it to your bank account.</li>
            </ol>
          </Card>

          <Card title="Risks">
            <p className="text-sm leading-relaxed whitespace-pre-line wrap-anywhere">{detail.riskDisclosure}</p>
          </Card>

          <Card title="Terms & conditions">
            <details>
              <summary className="cursor-pointer text-sm font-medium">Read the terms</summary>
              <div className="mt-3 max-h-80 overflow-y-auto text-sm leading-relaxed whitespace-pre-line text-muted wrap-anywhere">
                {detail.termsAndConditions}
              </div>
            </details>
          </Card>
        </div>

        <aside className="space-y-6 lg:sticky lg:top-6 lg:self-start">
          <section className="space-y-5 border border-border bg-surface p-6" aria-labelledby="calculator">
            <Eyebrow>Estimate your return</Eyebrow>
            <form className="grid grid-cols-2 items-end gap-3 [&>*]:min-w-0" action={`/marketplace/${id}`}>
              <TextField id="calculator" label="Containers" name="containers" type="number" inputMode="numeric" min={1} max={50}
                defaultValue={String(requested)} />
              <SelectField label="Lease" name="years" defaultValue={String(years)}
                options={leaseOptions(listing.minTenureYears, listing.maxTenureYears)} />
              <Button type="submit" className="col-span-2">Calculate</Button>
            </form>
            <dl className="grid grid-cols-2 gap-4 text-sm">
              {Number(listing.gstRatePercent) > 0 ? (
                <>
                  <Projection label="Price (before GST)" value={formatMoney(projection.amount)} />
                  <Projection label={`GST ${formatPercent(listing.gstRatePercent)}`} value={formatMoney(projection.gstAmount)}
                    note="Not part of the rent calculation" />
                  <Projection label="You pay (incl. GST)" value={formatMoney(projection.amountWithGst)} />
                </>
              ) : (
                <Projection label="You invest" value={formatMoney(projection.amount)} />
              )}
              <Projection label="Rent every month" value={formatMoney(projection.monthlyRent)}
                note={`${formatPercent(listing.monthlyRentPercent)} of ${formatMoney(projection.amount)}${Number(listing.gstRatePercent) > 0 ? " (before GST)" : ""}`} />
              <Projection label="Lease" value={`${yearsLabel(projection.tenureYears)} (${projection.payouts} payouts)`} />
            </dl>
            <p className="border-t border-border pt-3 text-sm">
              Total rent over the tenure: <strong className="font-display text-lg">{formatMoney(projection.totalRent)}</strong>
            </p>
            {projection.problems.length > 0 && (
              <ul className="space-y-1 text-sm text-amber-700 dark:text-amber-300">
                {projection.problems.map((problem) => <li key={problem}>• {problem}</li>)}
              </ul>
            )}
          </section>

          <Card title="Invest">
            {!investor ? (
              <Notice tone="info">Employee preview: this is what investors see. Investing needs an investor account.</Notice>
            ) : detail.eligibility.eligible ? (
              <div className="space-y-4">
                <Notice tone="success">
                  {inCart
                    ? `${inCart.quantity} container${inCart.quantity === 1 ? "" : "s"} of this plan ${inCart.quantity === 1 ? "is" : "are"} in your cart.`
                    : "You are eligible to invest in this plan."}
                </Notice>
                <AddToCartForm productId={id} defaultQuantity={requested} defaultYears={years}
                  minYears={listing.minTenureYears} maxYears={listing.maxTenureYears} inCart={Boolean(inCart)} />
                {inCart && <LinkButton href="/cart" variant="quiet" className="w-full">Go to cart</LinkButton>}
              </div>
            ) : (
              <div className="space-y-4">
                <ul className="space-y-2 text-sm">
                  {detail.eligibility.reasons.map((reason) => <li key={reason}>• {reason}</li>)}
                </ul>
                {detail.eligibility.reasons.some((r) => r.includes("identity")) && (
                  <LinkButton href="/profile/verification" className="w-full">Verify my identity</LinkButton>
                )}
                {detail.eligibility.reasons.some((r) => r.includes("bank account")) && (
                  <LinkButton href="/profile/bank-accounts" variant={detail.eligibility.reasons.some((r) => r.includes("identity")) ? "secondary" : "primary"}
                    className="w-full">Add a bank account</LinkButton>
                )}
              </div>
            )}
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Fact({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div className="bg-surface p-4">
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className={`mt-1 ${emphasis ? "font-display text-2xl font-semibold text-gold-text" : "font-semibold"}`}>{value}</dd>
    </div>
  );
}

function Projection({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-display text-xl font-semibold">{value}</dd>
      {note && <dd className="mt-0.5 text-xs text-muted">{note}</dd>}
    </div>
  );
}
