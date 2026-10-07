import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
import { CONTAINER_TYPE_LABEL, PRODUCT_STATUS_LABEL } from "@/features/marketplace/labels";
import { AddToCartForm } from "@/features/orders/add-to-cart-form";
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
  const [projection] = await Promise.all([
    authFetch<ReturnProjection>(`/api/v1/marketplace/${id}/projection?containers=${requested}`),
  ]);

  const photos = detail.photoIds;
  const docHref = (documentId: string) => `/marketplace/${id}/documents/${documentId}`;
  const type = CONTAINER_TYPE_LABEL[listing.containerType];

  return (
    <div className="space-y-10">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/marketplace" className="hover:text-foreground">Marketplace</Link> / <span className="font-mono">{listing.code}</span>
      </nav>

      <header className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-3">
          <div className="relative aspect-[16/9] overflow-hidden bg-ink">
            {photos[0] ? (
              // eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP
              <img src={docHref(photos[0])} alt={`A ${type} container`} className="size-full object-cover" />
            ) : (
              <ContainerScene focus="side" className="size-full" />
            )}
          </div>
          {photos.length > 1 && (
            <ul className="grid grid-cols-4 gap-3">
              {photos.slice(1, 5).map((photo) => (
                <li key={photo}>
                  <a href={docHref(photo)} target="_blank" rel="noopener noreferrer" className="block aspect-[4/3] overflow-hidden bg-ink">
                    {/* eslint-disable-next-line @next/next/no-img-element -- see above */}
                    <img src={docHref(photo)} alt={`A ${type} container`} loading="lazy" className="size-full object-cover" />
                  </a>
                </li>
              ))}
            </ul>
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
            <Fact label="Paid to you every month" value={formatPercent(listing.monthlyPayoutPercent)} emphasis />
            <Fact label="Price per container" value={formatMoney(listing.price)} />
            <Fact label={`Rent (${formatPercent(listing.monthlyRentPercent)} a month)`} value={formatMoney(detail.monthlyRent)} />
            <Fact label={`Capital back (${formatPercent(listing.monthlyCapitalReturnPercent)} a month)`} value={formatMoney(detail.monthlyCapitalReturn)} />
            <Fact label="Lease tenure" value={`${listing.tenureMonths} months`} />
            <Fact label="Total paid per container" value={formatMoney(listing.totalPayout)} />
          </dl>

          <div className="space-y-1 text-sm">
            <p>
              <strong>{listing.availableContainers}</strong>{" "}
              <span className="text-muted">{type} container{listing.availableContainers === 1 ? "" : "s"} available now</span>
            </p>
          </div>
        </div>
      </header>

      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-8">
          <Card title="About this plan">
            <div className="space-y-4 text-sm leading-relaxed whitespace-pre-line">{detail.description}</div>
          </Card>

          <Card title="How it works">
            <ol className="list-decimal space-y-2 pl-5 text-sm leading-relaxed">
              <li>Choose how many containers to buy at {formatMoney(listing.price)} each, and check out.</li>
              <li>Pay by bank within 30 minutes. Online transfer, cheque or cash deposit are all accepted.</li>
              <li>Once your payment is confirmed, a {type} container is assigned to you by its unique container number and its {listing.tenureMonths}-month lease starts.</li>
              <li>Every month for {listing.tenureMonths} months you receive {formatMoney(listing.monthlyPayout)} per container in your wallet: {formatMoney(detail.monthlyRent)} rent plus {formatMoney(detail.monthlyCapitalReturn)} of your capital back. You can withdraw it to your bank account.</li>
            </ol>
          </Card>

          <Card title="Risks">
            <p className="text-sm leading-relaxed whitespace-pre-line">{detail.riskDisclosure}</p>
          </Card>

          <Card title="Terms & conditions">
            <details>
              <summary className="cursor-pointer text-sm font-medium">Read the terms</summary>
              <div className="mt-3 max-h-80 overflow-y-auto text-sm leading-relaxed whitespace-pre-line text-muted">
                {detail.termsAndConditions}
              </div>
            </details>
          </Card>
        </div>

        <aside className="space-y-6 lg:sticky lg:top-6 lg:self-start">
          <section className="space-y-5 bg-ink p-6 text-on-ink" aria-labelledby="calculator">
            <Eyebrow tone="light">Estimate your return</Eyebrow>
            <form className="flex items-end gap-3" action={`/marketplace/${id}`}>
              <div className="flex-1 [&_input]:border-on-ink/30 [&_input]:bg-transparent [&_input]:text-on-ink [&_label]:text-on-ink/70">
                <TextField id="calculator" label="Containers" name="containers" type="number" inputMode="numeric" min={1} max={50}
                  defaultValue={String(requested)} />
              </div>
              <Button type="submit">Calculate</Button>
            </form>
            <dl className="grid grid-cols-2 gap-4 text-sm">
              <Projection label="You invest" value={formatMoney(projection.amount)} />
              <Projection label="Paid every month" value={formatMoney(projection.monthlyPayout)} />
              <Projection label={`Rent over ${projection.payouts} months`} value={formatMoney(projection.totalRent)} />
              <Projection label="Capital returned" value={formatMoney(projection.totalCapitalReturned)} />
            </dl>
            <p className="border-t border-on-ink/20 pt-3 text-sm">
              Total over the tenure: <strong className="font-display text-lg">{formatMoney(projection.totalPayout)}</strong>
            </p>
            {projection.problems.length > 0 && (
              <ul className="space-y-1 text-sm text-amber-300">
                {projection.problems.map((problem) => <li key={problem}>• {problem}</li>)}
              </ul>
            )}
          </section>

          <Card title="Invest">
            {!investor ? (
              <Notice tone="info">Staff preview: this is what investors see. Investing needs an investor account.</Notice>
            ) : detail.eligibility.eligible ? (
              <div className="space-y-4">
                <Notice tone="success">
                  {inCart
                    ? `${inCart.quantity} container${inCart.quantity === 1 ? "" : "s"} of this plan ${inCart.quantity === 1 ? "is" : "are"} in your cart.`
                    : "You are eligible to invest in this plan."}
                </Notice>
                <AddToCartForm productId={id} defaultQuantity={requested} available={listing.availableContainers}
                  inCart={Boolean(inCart)} />
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

function Projection({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-on-ink/60 uppercase">{label}</dt>
      <dd className="mt-1 font-display text-xl font-semibold">{value}</dd>
    </div>
  );
}
