import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { openTicketAction } from "@/features/support/actions";
import { OpenTicketForm } from "@/features/support/forms";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Order, Portfolio } from "@/types/order";
import type { SupportCategory } from "@/types/support";
import type { Withdrawal } from "@/types/withdrawal";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "New support request" };

export default async function NewTicketPage() {
  // The investor's recent records, so a request can point at the one it is about.
  const [orders, withdrawals, portfolio, categories] = await Promise.all([
    authFetch<PageResponse<Order>>("/api/v1/orders?size=20"),
    authFetch<PageResponse<Withdrawal>>("/api/v1/withdrawals?size=20"),
    authFetch<Portfolio>("/api/v1/portfolio"),
    authFetch<SupportCategory[]>("/api/v1/support/tickets/categories"),
  ]);
  const related = [
    ...orders.content.map((o) => ({ value: `ORDER:${o.id}`, label: `Order ${o.orderNumber} · ${formatMoney(o.total)}` })),
    ...withdrawals.content.map((w) => ({ value: `WITHDRAWAL:${w.id}`, label: `Withdrawal ${w.reference} · ${formatMoney(w.amount)}` })),
    ...portfolio.holdings.map((h) => ({ value: `HOLDING:${h.id}`, label: `${h.productTitle} · ${formatMoney(h.amount)}` })),
  ];
  return (
    <div className="space-y-6">
      <PageHeader title="New support request" description="Tell us what you need; you can attach screenshots or statements. As you type a subject, we suggest help articles that may answer it straight away." />
      <Card><OpenTicketForm action={openTicketAction} related={related} categories={categories} /></Card>
    </div>
  );
}
