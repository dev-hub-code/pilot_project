import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
import { CONDITION_LABEL, CONTAINER_TYPE_LABEL, RISK_LABEL, RISK_TONE } from "@/features/marketplace/labels";
import { AddToCartForm } from "@/features/orders/add-to-cart-form";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { MarketplaceDetail, ReturnProjection } from "@/types/marketplace";
import type { Cart } from "@/types/order";
import { formatDate, formatDateTime, humanize } from "@/utils/format";
import { formatMoney, formatPercent, FREQUENCY_LABEL } from "@/utils/money";

export const metadata: Metadata = { title: "Offering" };

const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;

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
  const requested = typeof query.amount === "string" && AMOUNT.test(query.amount) ? query.amount : listing.minimumInvestment.amount;
  const [projection, cart] = await Promise.all([
    authFetch<ReturnProjection>(`/api/v1/marketplace/${id}/projection?amount=${encodeURIComponent(requested)}`),
    investor ? authFetch<Cart>("/api/v1/cart") : Promise.resolve(null),
  ]);
  const inCart = cart?.items.find((line) => line.productId === id);

  const photos = detail.documents.filter((d) => d.purpose === "CONTAINER_PHOTO");
  const files = detail.documents.filter((d) => d.purpose !== "CONTAINER_PHOTO");
  const docHref = (documentId: string) => `/marketplace/${id}/documents/${documentId}`;
  const c = listing.container;

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
              <img src={docHref(photos[0].documentId)} alt={photos[0].title} className="size-full object-cover" />
            ) : (
              <ContainerScene focus="side" className="size-full" />
            )}
          </div>
          {photos.length > 1 && (
            <ul className="grid grid-cols-4 gap-3">
              {photos.slice(1, 5).map((photo) => (
                <li key={photo.documentId}>
                  <a href={docHref(photo.documentId)} target="_blank" rel="noopener noreferrer" className="block aspect-[4/3] overflow-hidden bg-ink">
                    {/* eslint-disable-next-line @next/next/no-img-element -- see above */}
                    <img src={docHref(photo.documentId)} alt={photo.title} loading="lazy" className="size-full object-cover" />
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="space-y-6">
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge tone={listing.investmentType === "HNI" ? "success" : "neutral"}>
                {listing.investmentType === "HNI" ? "Standalone · HNI" : "Shared · Retail"}
              </StatusBadge>
              <StatusBadge tone={RISK_TONE[listing.riskLevel]}>{RISK_LABEL[listing.riskLevel]}</StatusBadge>
              {listing.status !== "OPEN" && <StatusBadge tone="neutral">{humanize(listing.status)}</StatusBadge>}
            </div>
            <p className="font-mono text-sm text-muted">{listing.code}</p>
            <h1 className="text-3xl leading-tight font-bold tracking-tight sm:text-4xl">{listing.title}</h1>
            <p className="text-muted">{listing.summary}</p>
          </div>

          <dl className="grid grid-cols-2 gap-px border border-border bg-border">
            <Fact label="Expected annual yield" value={formatPercent(listing.expectedAnnualReturnPercent)} emphasis />
            <Fact label="Rental income" value={`${formatMoney(listing.expectedRentalAmount)}/${FREQUENCY_LABEL[listing.rentalFrequency]}`} />
            <Fact label="Container price" value={formatMoney(listing.price)} />
            <Fact label="Term" value={`${listing.durationMonths} months`} />
            <Fact label="Minimum investment" value={formatMoney(listing.minimumInvestment)} />
            <Fact label="Then in steps of" value={formatMoney(detail.investmentIncrement)} />
          </dl>

          <div className="space-y-2">
            <ProgressBar percent={listing.capacity.fundedPercent} label="Funding progress" />
            <div className="flex justify-between text-sm">
              <span><strong>{formatMoney(listing.capacity.committed)}</strong> <span className="text-muted">invested</span></span>
              <span><strong>{formatMoney(listing.capacity.available)}</strong> <span className="text-muted">available</span></span>
            </div>
            {listing.offerClosesAt && (
              <p className="text-xs text-muted">Offer closes {formatDateTime(listing.offerClosesAt)}</p>
            )}
          </div>
        </div>
      </header>

      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-8">
          <Card title="About this investment">
            <div className="space-y-4 text-sm leading-relaxed whitespace-pre-line">{detail.description}</div>
            {detail.lesseeName && (
              <p className="mt-4 text-sm"><span className="text-muted">Leased to:</span> <strong>{detail.lesseeName}</strong></p>
            )}
          </Card>

          <Card title="The container">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Spec label="Container number" value={c.containerNumber} mono />
              <Spec label="Type" value={CONTAINER_TYPE_LABEL[c.containerType]} />
              <Spec label="Condition" value={CONDITION_LABEL[c.condition]} />
              <Spec label="Capacity" value={`${c.capacityCbm} m³`} />
              <Spec label="Max gross / tare" value={`${c.maxGrossKg.toLocaleString("en-US")} / ${c.tareKg.toLocaleString("en-US")} kg`} />
              <Spec label="Built" value={`${c.manufactureYear}${c.manufacturer ? ` · ${c.manufacturer}` : ""}`} />
              <Spec label="Location" value={`${c.currentLocation}, ${c.locationCountry}`} />
              <Spec label="Status" value={humanize(c.status)} />
            </dl>
          </Card>

          <Card title="Risks">
            <p className="text-sm leading-relaxed whitespace-pre-line">{detail.riskDisclosure}</p>
          </Card>

          <Card title="Documents">
            {files.length === 0 ? (
              <p className="text-sm text-muted">No documents published for this offering yet.</p>
            ) : (
              <ul className="divide-y divide-border text-sm">
                {files.map((file) => (
                  <li key={file.documentId} className="flex items-center justify-between gap-3 py-3">
                    <span>
                      <span className="font-medium">{file.title}</span>{" "}
                      <span className="text-muted">· {humanize(file.purpose)}</span>
                    </span>
                    <a href={docHref(file.documentId)} target="_blank" rel="noopener noreferrer"
                      className="text-gold-text underline-offset-4 hover:underline">Open</a>
                  </li>
                ))}
              </ul>
            )}
            <details className="mt-6 border-t border-border pt-4">
              <summary className="cursor-pointer text-sm font-medium">Terms &amp; conditions (version {detail.termsVersion})</summary>
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
                <TextField id="calculator" label={`Amount (${listing.price.currency})`} name="amount" inputMode="decimal"
                  defaultValue={requested} />
              </div>
              <Button type="submit">Calculate</Button>
            </form>
            <dl className="grid grid-cols-2 gap-4 text-sm">
              <Projection label="Your ownership" value={formatPercent(projection.ownershipPercent, 4)} />
              <Projection label={`Per ${FREQUENCY_LABEL[listing.rentalFrequency]}`} value={formatMoney(projection.rentalPerPayment)} />
              <Projection label="Per year" value={formatMoney(projection.expectedAnnualIncome)} />
              <Projection label={`Over ${projection.paymentsOverTerm} payments`} value={formatMoney(projection.expectedIncomeOverTerm)} />
            </dl>
            {projection.problems.length > 0 && (
              <ul className="space-y-1 text-sm text-amber-300">
                {projection.problems.map((problem) => <li key={problem}>• {problem}</li>)}
              </ul>
            )}
            <p className="text-xs text-on-ink/60">
              Expected figures assume the lessee pays rent as forecast. They are not guaranteed.
            </p>
          </section>

          <Card title="Invest">
            {!investor ? (
              <Notice tone="info">Staff preview: this is what investors see. Investing needs an investor account.</Notice>
            ) : detail.eligibility.eligible ? (
              <div className="space-y-4">
                <Notice tone="success">
                  {inCart ? `${formatMoney(inCart.amount)} of this offering is in your cart.` : "You are eligible to invest in this offering."}
                </Notice>
                <AddToCartForm productId={id} currency={listing.price.currency} defaultAmount={inCart?.amount.amount ?? requested}
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
            {detail.offerOpensAt && <p className="mt-4 text-xs text-muted">Offer opened {formatDate(detail.offerOpensAt)}</p>}
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

function Spec({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className={`mt-1 font-medium ${mono ? "font-mono" : ""}`}>{value}</dd>
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
