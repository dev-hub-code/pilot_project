import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { formatPeriod, plusMonths, todayUtc } from "@/features/earnings/labels";
import { RecordRentalForm } from "@/features/admin/rental-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Product } from "@/types/marketplace";
import { formatMoney, FREQUENCY_LABEL } from "@/utils/money";

export const metadata: Metadata = { title: "Record rental payment" };

const MONTHS_PER_PERIOD = { MONTHLY: 1, QUARTERLY: 3 } as const;

export default async function RecordRentalPage({ searchParams }: PageProps<"/admin/rentals/new">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.RENTAL_RECORD)) redirect("/admin/rentals");
  const params = await searchParams;
  const productId = String(params.productId ?? "");
  if (!isUuid(productId)) notFound();
  let product: Product;
  try {
    product = await authFetch<Product>(`/api/v1/admin/investment-products/${productId}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }

  const months = MONTHS_PER_PERIOD[product.rentalFrequency];
  const count = Math.floor(product.durationMonths / months);
  const start = product.leaseStartsOn;
  // Same schedule as the backend: period n runs from start + (n − 1) periods to start + n periods.
  const periods = start
    ? Array.from({ length: count }, (_, i) => ({
      value: String(i + 1),
      label: `Period ${i + 1} · ${formatPeriod(plusMonths(start, i * months), plusMonths(start, (i + 1) * months))}`,
    }))
    : [];

  return (
    <div className="space-y-6">
      <PageHeader title="Record rental payment"
        description={`${product.code} · ${product.title} · ${formatMoney(product.expectedRentalAmount)} expected per ${FREQUENCY_LABEL[product.rentalFrequency]}`} />
      {product.status !== "ACTIVE" ? (
        <Notice tone="warning">
          Rent can only be recorded while an offering is on lease. {" "}
          <Link href={`/admin/products/${product.id}`} className="underline">Back to {product.code}</Link>
        </Notice>
      ) : (
        <Card title="Payment from the lessee">
          <RecordRentalForm productId={product.id} periods={periods} defaultPeriod={String(params.period ?? "1")}
            expected={product.expectedRentalAmount.amount} currency={product.expectedRentalAmount.currency} today={todayUtc()} />
        </Card>
      )}
    </div>
  );
}
