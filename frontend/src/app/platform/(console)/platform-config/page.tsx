import Link from "next/link";
import { AlertTriangle, ArrowRight, Settings } from "lucide-react";
import { configTabs } from "@/components/layout/admin-nav";
import { CONFIG_SECTIONS } from "@/features/admin/config-sections";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { RentCreditSettings } from "@/types/earning";
import type { CompanyBankAccount } from "@/types/order";
import type { PaymentGatewaySettings } from "@/types/payment-gateway";
import type { RateVersion } from "@/types/referral";
import type { WithdrawalRun, WithdrawalSettings } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

type Fact = { label: string; value: string; warn?: boolean };

const BASE = "/platform/platform-config";

/** Every area of Platform config the viewer may open, as cards with what is set there now. */
export default async function PlatformConfigPage() {
  const session = await requireStaff();
  const tabs = configTabs(session.permissions);
  const has = (path: string) => tabs.some((t) => t.href === `${BASE}/${path}`);
  // Each card's figures; a section that fails to load still shows its card.
  const load = <T,>(show: boolean, path: string) => (show ? authFetch<T>(path).catch(() => null) : Promise.resolve(null));
  const [withdrawals, runs, rent, rates, gateway, accounts] = await Promise.all([
    load<WithdrawalSettings>(has("withdrawals"), "/api/v1/admin/settings/withdrawals"),
    load<WithdrawalRun[]>(has("withdrawals"), "/api/v1/admin/withdrawal-runs"),
    load<RentCreditSettings>(has("returns"), "/api/v1/admin/settings/rent-credit"),
    load<RateVersion[]>(has("referral-rates"), "/api/v1/admin/referral-rates"),
    load<PaymentGatewaySettings>(has("payment-gateway"), "/api/v1/admin/settings/payment-gateway"),
    load<CompanyBankAccount[]>(has("company-accounts"), "/api/v1/admin/company-bank-accounts"),
  ]);

  const facts: Record<string, Fact[]> = {};
  if (withdrawals) {
    const money = (n: number) => formatMoney({ amount: String(n), currency: withdrawals.currency });
    const last = runs?.reduce<WithdrawalRun | undefined>((latest, r) => (!latest || r.runAt > latest.runAt ? r : latest), undefined);
    facts[`${BASE}/withdrawals`] = [
      { label: "Minimum", value: money(withdrawals.minimumAmount) },
      { label: "Charges", value: `${withdrawals.feeFlat > 0 ? `${money(withdrawals.feeFlat)} + ` : ""}${withdrawals.feePercent}% · TDS ${withdrawals.taxPercent}%` },
      { label: "Last run", value: last ? formatDateTime(last.runAt) : "None yet" },
    ];
  }
  if (rent) facts[`${BASE}/returns`] = [{ label: "Credited", value: rent.label.charAt(0).toUpperCase() + rent.label.slice(1) }];
  if (rates) {
    const current = rates.find((r) => r.state === "IN_FORCE");
    const scheduled = rates.filter((r) => r.state === "SCHEDULED").length;
    facts[`${BASE}/referral-rates`] = [
      { label: "Levels 1-4", value: current ? current.percents.map((p) => formatPercent(p, 2)).join(" · ") : "Not set", warn: !current },
      { label: "Online, once", value: current ? formatPercent(current.oneTimePercent, 2) : "Not set" },
      ...(scheduled > 0 ? [{ label: "Scheduled", value: `${scheduled} change${scheduled === 1 ? "" : "s"}` }] : []),
    ];
  }
  if (gateway) {
    facts[`${BASE}/payment-gateway`] = [
      { label: "API keys", value: !gateway.keysConfigured ? "Not set" : gateway.mode === "live" ? "Live" : "Test", warn: !gateway.keysConfigured },
      { label: "Online payments", value: gateway.onlinePayments ? "On" : "Off" },
      { label: "Webhook", value: gateway.webhookConfigured ? "Set" : "Not set", warn: !gateway.webhookConfigured },
    ];
  }
  if (accounts) {
    const active = accounts.filter((a) => a.active).length;
    facts[`${BASE}/company-accounts`] = [
      { label: "Offered to investors", value: `${active} of ${accounts.length}`, warn: active === 0 },
    ];
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {tabs.map((tab) => {
        const section = CONFIG_SECTIONS[tab.href];
        const Icon = section?.icon ?? Settings;
        const list = facts[tab.href] ?? [];
        const needsLook = list.some((f) => f.warn);
        return (
          <li key={tab.href}
            className={`group flex flex-col gap-4 rounded-2xl border bg-surface p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-md ${needsLook ? "border-amber-500/40" : "border-border"}`}>
            <div className="flex items-start justify-between gap-3">
              <span aria-hidden="true" className="flex size-11 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm transition-colors group-hover:bg-gold group-hover:text-on-gold dark:bg-gold dark:text-on-gold">
                <Icon className="size-5" />
              </span>
              {needsLook && (
                <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-300">
                  <AlertTriangle aria-hidden="true" className="size-3" /> Needs a look
                </span>
              )}
            </div>
            <div className="space-y-1">
              <h2 className="font-semibold tracking-tight">
                <Link href={tab.href} className="card-link">{tab.label}</Link>
              </h2>
              {section && <p className="text-sm text-muted">{section.blurb}</p>}
            </div>
            {list.length > 0 && (
              <dl className="divide-y divide-border rounded-xl bg-background/60 text-sm ring-1 ring-border">
                {list.map((f) => (
                  <div key={f.label} className="flex items-center justify-between gap-3 px-3 py-2">
                    <dt className="text-xs text-muted">{f.label}</dt>
                    <dd className={`text-right font-medium tabular-nums ${f.warn ? "text-amber-700 dark:text-amber-300" : ""}`}>{f.value}</dd>
                  </div>
                ))}
              </dl>
            )}
            <span aria-hidden="true" className="mt-auto inline-flex items-center gap-1 text-sm font-medium text-gold-text">
              Open <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
            </span>
          </li>
        );
      })}
    </ul>
  );
}
