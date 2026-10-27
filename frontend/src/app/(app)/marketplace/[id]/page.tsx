import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { CONTAINER_TYPE_LABEL, PRODUCT_STATUS_LABEL } from "@/features/marketplace/labels";
import { InvestmentPlan } from "@/features/marketplace/investment-plan";
import { PhotoSlider } from "@/features/marketplace/photo-slider";
import { ReturnCalculator } from "@/features/marketplace/return-calculator";
import { InvestNowForm } from "@/features/orders/invest-now-form";
import { yearsLabel } from "@/features/orders/lease-years";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { onboardingHref } from "@/lib/server/onboarding";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { MarketplaceDetail, MinimumOrder, RentTiers, ReturnProjection } from "@/types/marketplace";
import type { Cart } from "@/types/order";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Container" };

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
  // From the calculator, if the investor used it; "Invest now" buys that many.
  const calculated = typeof query.containers === "string" && CONTAINERS.test(query.containers) && Number(query.containers) >= 1
    ? Number(query.containers) : null;
  const requested = calculated ?? inCart?.quantity ?? 1;
  // The lease the investor is looking at: from the calculator, else the cart, else the plan's shortest.
  const asked = typeof query.years === "string" && CONTAINERS.test(query.years) ? Number(query.years) : null;
  const years = asked !== null && asked >= listing.minTenureYears && asked <= listing.maxTenureYears
    ? asked : inCart?.tenureYears ?? listing.minTenureYears;
  const [projection, rentTiers, minimumOrder] = await Promise.all([
    authFetch<ReturnProjection>(`/api/v1/marketplace/${id}/projection?containers=${requested}&years=${years}`),
    authFetch<RentTiers>("/api/v1/public/rent-tiers"),
    authFetch<MinimumOrder>("/api/v1/public/minimum-order"),
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
        <Link href="/marketplace" className="hover:text-foreground">Marketplace</Link> / <span>{listing.title}</span>
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
            <h1 className="text-3xl leading-tight font-bold tracking-tight sm:text-4xl">{listing.title}</h1>
            <p className="text-muted">{listing.summary}</p>
          </div>

          {/* Side by side only when both fit (a price is wide); stacked on phones and in a narrow column. */}
          <dl className="grid grid-cols-[repeat(auto-fit,minmax(13rem,1fr))] gap-px border border-border bg-border">
            <Fact label="Price per container" value={formatMoney(listing.price)} emphasis />
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
          <Card title="About this container">
            <div className="space-y-4 text-sm leading-relaxed whitespace-pre-line wrap-anywhere">{detail.description}</div>
          </Card>

          <Card title="How it works">
            <ol className="list-decimal space-y-2 pl-5 text-sm leading-relaxed">
              <li>Choose how many containers to buy at {formatMoney(listing.price)} each, and check out.</li>
              <li>Pay by bank within 30 minutes. Online transfer or cheque are accepted.</li>
              <li>Choose your lease: whole years, from {leaseRange}.</li>
              <li>Once your payment is confirmed, a {type} container is assigned to you by its unique container number and the lease you chose starts.</li>
              <li>Every month of your lease you receive rent in your wallet, at the rate for your order&apos;s total value (see the calculator). You can withdraw it to your bank account.</li>
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

        {/* The same containers and lease the calculator starts from, so "Invest now" buys what it shows. */}
        <InvestmentPlan quantity={String(requested)} years={String(years)}>
          <aside className="space-y-6 lg:sticky lg:top-6 lg:self-start">
            <section className="space-y-5 border border-border bg-surface p-6" aria-labelledby="calculator">
              <Eyebrow>Estimate your return</Eyebrow>
              <ReturnCalculator productId={id} minYears={listing.minTenureYears} maxYears={listing.maxTenureYears} initial={projection} />
              {/* Invest straight from the calculation: no second form to fill in. */}
              {!investor ? (
                <Notice tone="info">Employee preview: this is what investors see. Investing needs an investor account.</Notice>
              ) : detail.eligibility.eligible ? (
                <InvestNowForm productId={id} inCart={inCart?.quantity ?? 0} />
              ) : (
                // The calculator lists what is missing; onboarding leads through it (identity, then a bank account).
                <LinkButton href={await onboardingHref()} className="w-full">Complete onboarding</LinkButton>
              )}
              <div className="border-t border-border pt-3 text-xs text-muted">
                <p className="font-medium text-foreground">Rent by order value</p>
                <p>Your whole order (every container in your cart) earns the rate for its total, on each container.</p>
                {Number(minimumOrder.amount.amount) > 0 && (
                  <p>The minimum order is {formatMoney(minimumOrder.amount)}.</p>
                )}
                <ul className="mt-1 space-y-0.5 tabular-nums">
                  {rentTiers.tiers.map((t) => (
                    <li key={t.fromAmount.amount}>
                      {formatMoney(t.fromAmount)}{t.toAmount ? ` – ${formatMoney(t.toAmount)}` : " and above"}:
                      {" "}{formatPercent(t.monthlyRentPercent)} a month
                    </li>
                  ))}
                </ul>
              </div>
            </section>
          </aside>
        </InvestmentPlan>
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

