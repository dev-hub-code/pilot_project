import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { PaymentGatewaySettings } from "@/types/payment-gateway";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { PaymentGatewayForm } from "./payment-gateway-form";

/** How online payments run: editable by those who manage system settings, visible to finance. */
export async function PaymentGatewayCard({ permissions }: { permissions: readonly string[] }) {
  const s = await authFetch<PaymentGatewaySettings>("/api/v1/admin/settings/payment-gateway");
  const canEdit = hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE);
  const limit = Number(s.maxAmount.amount) > 0 ? formatMoney(s.maxAmount) : "no limit";
  const d = s.defaults;

  return (
    <div className="space-y-6">
      <Card title="Razorpay connection"
        description="The API keys and webhook secret are set on the server (environment variables), never here.">
        <dl className="grid gap-4 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-xs text-muted">API keys</dt>
            <dd className="mt-1">
              {s.keysConfigured
                ? <StatusBadge tone={s.mode === "live" ? "success" : "warning"}>{s.mode === "live" ? "Live keys" : "Test keys"}</StatusBadge>
                : <StatusBadge tone="danger">Not set</StatusBadge>}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Webhook secret</dt>
            <dd className="mt-1">{s.webhookConfigured ? <StatusBadge tone="success">Set</StatusBadge> : <StatusBadge tone="warning">Not set</StatusBadge>}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Online investors pay</dt>
            <dd className="mt-1">
              {!s.keysConfigured || !s.onlinePayments
                ? "By bank transfer to a company account, or from their balance"
                : `Online checkout${s.virtualAccounts ? " or bank transfer" : ""}, or from their balance`}
            </dd>
          </div>
        </dl>
        {!s.keysConfigured && (
          <p className="mt-3 text-xs text-muted">Until the keys are set, the settings below are kept but have no effect.</p>
        )}
      </Card>

      <Card title="Online payment settings"
        description="Apply to payments started from now on. Payments already started still complete.">
        {canEdit ? <PaymentGatewayForm settings={s} /> : (
          <p className="text-sm">
            Online payments {s.onlinePayments ? "on" : "off"} · bank transfer {s.virtualAccounts ? "on" : "off"} ·
            checkout limit {limit} · checkout hold {s.checkoutWindowMinutes} min · bank transfer hold {s.virtualAccountWindowHours} h
            <span className="text-muted"> · only admins change these.</span>
          </p>
        )}
        <p className="mt-3 text-xs text-muted">
          {s.updatedAt
            ? `Last changed ${formatDateTime(s.updatedAt)}${s.updatedByName ? ` by ${s.updatedByName}` : ""}.`
            : `The server's defaults apply until saved here: online payments ${d.onlinePayments ? "on" : "off"}, bank transfer ${d.virtualAccounts ? "on" : "off"}, checkout limit ${Number(d.maxAmount.amount) > 0 ? formatMoney(d.maxAmount) : "none"}, holds of ${d.checkoutWindowMinutes} minutes and ${d.virtualAccountWindowHours} hours.`}
        </p>
      </Card>
    </div>
  );
}
