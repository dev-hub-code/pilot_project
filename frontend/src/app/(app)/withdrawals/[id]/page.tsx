import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Download, Landmark, LifeBuoy, MessageCircleQuestion, PauseCircle, Ban, CircleX } from "lucide-react";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { EARNINGS_BALANCE_LABEL } from "@/features/earnings/labels";
import { CANCELLABLE, investorStatusLabel, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { CancelWithdrawalForm, RespondWithdrawalForm } from "@/features/withdrawals/tracking-forms";
import { Breakdown, deduction, ProgressStepper, Timeline } from "@/features/withdrawals/ui";
import { authFetch } from "@/lib/server/auth/session";
import { BackendError } from "@/lib/server/backend-client";
import type { Withdrawal, WithdrawalEvent } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** One withdrawal, tracked: where it is, what it pays, every step so far, and what the investor can do now. */
export default async function WithdrawalTrackingPage({ params }: PageProps<"/withdrawals/[id]">) {
  const { id } = await params;
  if (!UUID.test(id)) notFound();
  let w: Withdrawal;
  let timeline: WithdrawalEvent[];
  try {
    [w, timeline] = await Promise.all([
      authFetch<Withdrawal>(`/api/v1/withdrawals/${id}`),
      authFetch<WithdrawalEvent[]>(`/api/v1/withdrawals/${id}/timeline`),
    ]);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const ended = ["REJECTED", "CANCELLED", "RETURNED"].includes(w.status);
  const cash = w.payoutMethod === "OFFICE_CASH";

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <Link href="/withdrawals" className="inline-flex items-center gap-1.5 text-sm text-muted hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden="true" /> Withdrawals
      </Link>

      {/* Summary header */}
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div className="space-y-1">
            <p className="font-mono text-sm text-muted">{w.reference}</p>
            <p className="font-display text-4xl font-semibold tracking-tight tabular-nums">{formatMoney(w.net)}</p>
            <p className="text-sm text-muted">
              {cash ? "Cash at our office" : <>To {w.bankName} <span className="font-mono">{w.bankAccountMasked}</span></>} · requested {formatDateTime(w.createdAt)}
            </p>
          </div>
          <div className="space-y-2 text-right">
            <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{investorStatusLabel(w.status, w.infoRequested)}</StatusBadge>
            <p className="text-xs text-muted">Updated {formatDateTime(w.statusChangedAt)}</p>
            {w.status === "PAID" && (
              <a href={`/withdrawals/${w.id}/receipt`}
                className="inline-flex min-h-10 items-center gap-2 rounded-lg border border-border px-4 text-sm font-medium hover:border-gold hover:text-gold-text">
                <Download className="size-4" aria-hidden="true" /> Receipt (PDF)
              </a>
            )}
          </div>
        </div>
        {!ended && <div className="mt-8"><ProgressStepper status={w.status} timeline={timeline} /></div>}
      </section>

      {/* What needs attention */}
      {w.status === "ON_HOLD" && w.infoRequested && (
        <Alert icon={MessageCircleQuestion} tone="amber" title="We need more information from you" text={w.holdReason}>
          <div className="mt-4"><RespondWithdrawalForm withdrawalId={w.id} /></div>
        </Alert>
      )}
      {w.status === "ON_HOLD" && !w.infoRequested && (
        <Alert icon={PauseCircle} tone="amber" title="Your withdrawal is on hold" text={w.holdReason} />
      )}
      {w.status === "REJECTED" && (
        <Alert icon={Ban} tone="rose" title="Your withdrawal was not approved" text={`${w.rejectionReason ?? ""} The amount is back in your wallet.`} />
      )}
      {w.status === "FAILED" && (
        <Alert icon={CircleX} tone="rose" title="The payment did not go through"
          text={`${w.failureReason ? `${w.failureReason}. ` : ""}The amount is held safely while our finance team sorts it out.`}>
          {w.ticketId && (
            <Link href={`/support/${w.ticketId}`} className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium underline">
              <LifeBuoy className="size-4" aria-hidden="true" /> See the support ticket
            </Link>
          )}
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_1.1fr]">
        <div className="space-y-6">
          <Card title="Amount">
            <Breakdown rows={[
              { label: "Withdrawn from", value: EARNINGS_BALANCE_LABEL[w.balance] },
              { label: "Amount", value: formatMoney(w.amount) },
              { label: "Charges", value: deduction(w.fee), muted: true },
              { label: "Tax withheld (TDS)", value: deduction(w.tax), muted: true },
              { label: "You receive", value: formatMoney(w.net), strong: true },
            ]} />
          </Card>
          <Card title="Payment">
            <div className="flex items-start gap-4 text-sm">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><Landmark className="size-5" aria-hidden="true" /></span>
              <div className="space-y-1">
                <p className="font-medium">{cash ? "Cash at our office" : `${w.bankName ?? "Bank account"} ${w.bankAccountMasked ?? ""}`}</p>
                {!cash && w.bankHolderName && <p className="text-muted">{w.bankHolderName}</p>}
                {w.payoutReference && (
                  <p className="text-muted">{cash ? "Receipt number" : "Bank reference (UTR)"}: <span className="font-mono text-foreground">{w.payoutReference}</span></p>
                )}
                {w.closedAt && w.status === "PAID" && <p className="text-muted">Paid {formatDateTime(w.closedAt)}</p>}
              </div>
            </div>
            {CANCELLABLE.includes(w.status) && (
              <div className="mt-5 border-t border-border pt-5">
                <p className="mb-3 text-sm text-muted">Changed your mind? You can cancel until the withdrawal is approved.</p>
                <CancelWithdrawalForm withdrawalId={w.id} />
              </div>
            )}
          </Card>
        </div>
        <Card title="Activity">
          <Timeline events={timeline} label={(s) => investorStatusLabel(s)} />
        </Card>
      </div>
    </div>
  );
}

function Alert({ icon: Icon, tone, title, text, children }: {
  icon: typeof Ban;
  tone: "amber" | "rose";
  title: string;
  text: string | null;
  children?: React.ReactNode;
}) {
  const colours = tone === "amber"
    ? "border-amber-500/30 bg-amber-500/5 [&_.icon]:bg-amber-500/15 [&_.icon]:text-amber-700 dark:[&_.icon]:text-amber-400"
    : "border-rose-500/30 bg-rose-500/5 [&_.icon]:bg-rose-500/15 [&_.icon]:text-rose-700 dark:[&_.icon]:text-rose-400";
  return (
    <section className={`flex gap-4 rounded-xl border p-5 ${colours}`}>
      <span className="icon flex size-10 shrink-0 items-center justify-center rounded-full"><Icon className="size-5" aria-hidden="true" /></span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        {text && <p className="mt-0.5 text-sm text-muted">{text}</p>}
        {children}
      </div>
    </section>
  );
}
