import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { InvoiceDocument } from "@/features/orders/invoice-document";
import { PrintButton } from "@/features/orders/print-button";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Invoice } from "@/types/order";

export const metadata: Metadata = { title: "Invoice" };

export default async function InvoicePage({ params }: PageProps<"/orders/[id]/invoice">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  let invoice: Invoice;
  try {
    invoice = await authFetch<Invoice>(`/api/v1/orders/${id}/invoice`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 print:hidden">
        <nav aria-label="Breadcrumb" className="text-sm text-muted">
          <Link href="/orders" className="hover:text-foreground">Orders</Link> /{" "}
          <Link href={`/orders/${id}`} className="font-mono hover:text-foreground">{invoice.orderNumber}</Link> / Invoice
        </nav>
        <div className="flex items-center gap-3">
          <a href={`/orders/${id}/invoice/pdf`} className="text-sm text-gold-text hover:underline">Download PDF</a>
          <PrintButton />
        </div>
      </div>
      <InvoiceDocument invoice={invoice} />
    </div>
  );
}
