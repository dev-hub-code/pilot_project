import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { PublicFrame } from "@/features/marketplace/public-frame";
import { yearsLabel } from "@/features/orders/lease-years";
import { BackendError } from "@/lib/server/backend-client";
import { openListing, openPhotoSrc, openRentBands } from "@/lib/server/open-marketplace";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Container" };

/** One container listing for visitors who are not signed in: its terms, photos and the rent bands. */
export default async function PublicListingPage({ params }: PageProps<"/containers/[code]">) {
  const { code } = await params;
  const [listing, bands] = await Promise.all([
    openListing(code).catch((error) => {
      if (error instanceof BackendError && error.status === 404) notFound();
      throw error;
    }),
    openRentBands().catch(() => []),
  ]);
  const lease = listing.minTenureYears === listing.maxTenureYears ? yearsLabel(listing.minTenureYears)
    : `${listing.minTenureYears}–${listing.maxTenureYears} years`;
  const open = listing.status === "OPEN";

  return (
    <PublicFrame next="/marketplace">
      <div className="space-y-8">
        <Link href="/containers" className="text-sm text-muted hover:text-foreground">← All containers</Link>
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="space-y-6">
            <header className="space-y-3">
              <Eyebrow>{listing.containerTypeLabel} · {listing.code}</Eyebrow>
              <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{listing.title}</h1>
              <p className="text-muted">{listing.summary}</p>
            </header>
            {listing.photoIds.length > 0 ? (
              <ul className="grid gap-3 sm:grid-cols-2">
                {listing.photoIds.map((id) => (
                  <li key={id} className="aspect-[16/10] overflow-hidden bg-ink">
                    {/* eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP */}
                    <img src={openPhotoSrc(listing.code, id)} alt="" loading="lazy" className="size-full object-cover" />
                  </li>
                ))}
              </ul>
            ) : (
              <div className="aspect-[16/7] overflow-hidden bg-ink"><ContainerScene focus="side" className="size-full" /></div>
            )}
            {listing.description && (
              <Card title="About this container">
                <p className="text-sm leading-relaxed whitespace-pre-line wrap-anywhere">{listing.description}</p>
              </Card>
            )}
          </div>

          <aside className="space-y-6">
            <Card title={open ? "Open for investment" : "Closed"}>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">Price per container</dt>
                  <dd className="font-display text-2xl font-semibold text-gold-text">{formatMoney(listing.price)}</dd>
                </div>
                <div>
                  <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">Lease</dt>
                  <dd className="font-medium">{lease}, your choice</dd>
                </div>
              </dl>
              <div className="mt-5 flex flex-wrap gap-3">
                <LinkButton href={`/login?next=${encodeURIComponent("/marketplace")}`}>Sign in to invest</LinkButton>
                <LinkButton href="/register" variant="secondary">Register</LinkButton>
              </div>
            </Card>
            {bands.length > 0 && (
              <Card title="Monthly rent" description="Set by your order's total value, on every container's price.">
                <table className="w-full text-sm">
                  <tbody>
                    {bands.map((band) => (
                      <tr key={band.fromAmount.amount} className="border-b border-border last:border-0">
                        <td className="py-2 text-muted">
                          {formatMoney(band.fromAmount)}{band.toAmount ? ` – ${formatMoney(band.toAmount)}` : " and above"}
                        </td>
                        <td className="py-2 text-right font-medium tabular-nums">{formatPercent(band.monthlyRentPercent, 2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>
            )}
          </aside>
        </div>
      </div>
    </PublicFrame>
  );
}
