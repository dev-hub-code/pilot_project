import type { Invoice } from "@/types/order";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

/** The invoice as a printable document (print styles strip the surrounding app chrome). */
export function InvoiceDocument({ invoice }: { invoice: Invoice }) {
  return (
    <article className="mx-auto max-w-3xl space-y-10 border border-border bg-surface p-8 sm:p-12 print:max-w-none print:border-0 print:p-0">
      <header className="flex flex-wrap items-start justify-between gap-6">
        <div className="space-y-1 text-sm">
          <p className="text-lg font-semibold">{invoice.issuer.name}</p>
          <p className="whitespace-pre-line text-muted">{invoice.issuer.address}</p>
          {invoice.issuer.taxId && <p className="text-muted">Tax ID {invoice.issuer.taxId}</p>}
        </div>
        <div className="space-y-1 text-right">
          <h1 className="text-3xl font-semibold tracking-tight">Invoice</h1>
          <p className="font-mono text-sm">{invoice.invoiceNumber}</p>
        </div>
      </header>

      <dl className="grid gap-6 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">Billed to</dt>
          <dd className="mt-1 space-y-0.5">
            <p className="font-medium">{invoice.buyer.name}</p>
            {invoice.buyer.address && <p className="text-muted">{invoice.buyer.address}</p>}
            <p className="text-muted">{invoice.buyer.email}</p>
          </dd>
        </div>
        <div>
          <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">Issued</dt>
          <dd className="mt-1 font-medium">{formatDate(invoice.issuedAt)}</dd>
        </div>
        <div>
          <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">Order</dt>
          <dd className="mt-1 font-mono font-medium">{invoice.orderNumber}</dd>
        </div>
      </dl>

      <table className="w-full text-left text-sm">
        <thead className="border-b border-foreground/80 text-xs uppercase tracking-[0.08em] text-muted">
          <tr>
            <th scope="col" className="py-2 pr-4 font-medium">#</th>
            <th scope="col" className="py-2 pr-4 font-medium">Description</th>
            <th scope="col" className="py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {invoice.lines.map((line) => (
            <tr key={line.lineNumber}>
              <td className="py-3 pr-4 text-muted">{line.lineNumber}</td>
              <td className="py-3 pr-4">{line.description}</td>
              <td className="py-3 text-right tabular-nums">{formatMoney(line.amount)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="border-t border-foreground/80">
            <th scope="row" colSpan={2} className="pt-4 text-right font-medium">Total paid</th>
            <td className="pt-4 text-right font-display text-xl font-semibold tabular-nums">{formatMoney(invoice.total)}</td>
          </tr>
        </tfoot>
      </table>

      {invoice.notes && <p className="text-xs text-muted">{invoice.notes}</p>}
    </article>
  );
}
