import { Clock, CreditCard, KeyRound, Landmark, type LucideIcon, Server, Settings2, Timer, Wallet, Webhook } from "lucide-react";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { PaymentGatewaySettings } from "@/types/payment-gateway";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { ChangedNote, ConfigPanel, ConfigStat, HowItWorks } from "./config-ui";
import { PaymentGatewayForm } from "./payment-gateway-form";

type Tone = "good" | "warn" | "bad";

const TONE: Record<Tone, { tile: string; dot: string }> = {
  good: { tile: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300", dot: "bg-emerald-500" },
  warn: { tile: "bg-amber-500/10 text-amber-700 dark:text-amber-300", dot: "bg-amber-500" },
  bad: { tile: "bg-red-500/10 text-red-700 dark:text-red-300", dot: "bg-red-500" },
};

/** How online payments run: editable by those who manage system settings, visible to finance. */
export async function PaymentGatewayCard({ permissions }: { permissions: readonly string[] }) {
  const s = await authFetch<PaymentGatewaySettings>("/api/v1/admin/settings/payment-gateway");
  const canEdit = hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE);
  const limit = Number(s.maxAmount.amount) > 0 ? formatMoney(s.maxAmount) : "No limit";
  const d = s.defaults;
  const pays = !s.keysConfigured || !s.onlinePayments
    ? "By bank transfer to a company account, or from their balance"
    : `Online checkout${s.virtualAccounts ? " or bank transfer" : ""}, or from their balance`;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="min-w-0 space-y-6">
        <ConfigPanel icon={Server} title="Razorpay connection"
          description="The API keys and webhook secret are set on the server (environment variables), never here.">
          <div className="grid gap-3 sm:grid-cols-2">
            <Status icon={KeyRound} label="API keys"
              tone={!s.keysConfigured ? "bad" : s.mode === "live" ? "good" : "warn"}
              value={!s.keysConfigured ? "Not set" : s.mode === "live" ? "Live keys" : "Test keys"} />
            <Status icon={Webhook} label="Webhook secret" tone={s.webhookConfigured ? "good" : "warn"}
              value={s.webhookConfigured ? "Set" : "Not set"} />
          </div>
          <div className="flex items-start gap-3 rounded-xl bg-background/60 p-3 text-sm ring-1 ring-border">
            <Wallet aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-gold-text" />
            <p><span className="text-muted">Online investors pay: </span><span className="font-medium">{pays}</span></p>
          </div>
          {!s.keysConfigured && (
            <p className="rounded-xl bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
              Until the keys are set, the settings below are kept but have no effect.
            </p>
          )}
        </ConfigPanel>

        <ConfigPanel icon={Settings2} title="Online payment settings"
          description="Apply to payments started from now on. Payments already started still complete.">
          {canEdit ? <PaymentGatewayForm settings={s} /> : (
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              <ConfigStat icon={CreditCard} label="Online payments" value={s.onlinePayments ? "On" : "Off"} />
              <ConfigStat icon={Landmark} label="Bank transfer" value={s.virtualAccounts ? "On" : "Off"} />
              <ConfigStat icon={Wallet} label="Checkout limit" value={limit} />
              <ConfigStat icon={Timer} label="Checkout hold" value={`${s.checkoutWindowMinutes} min`} />
              <ConfigStat icon={Clock} label="Bank transfer hold" value={`${s.virtualAccountWindowHours} h`} hint="Only admins change these." />
            </div>
          )}
          <ChangedNote>
            {s.updatedAt
              ? `Last changed ${formatDateTime(s.updatedAt)}${s.updatedByName ? ` by ${s.updatedByName}` : ""}.`
              : `The server's defaults apply until saved here: online payments ${d.onlinePayments ? "on" : "off"}, bank transfer ${d.virtualAccounts ? "on" : "off"}, checkout limit ${Number(d.maxAmount.amount) > 0 ? formatMoney(d.maxAmount) : "none"}, holds of ${d.checkoutWindowMinutes} minutes and ${d.virtualAccountWindowHours} hours.`}
          </ChangedNote>
        </ConfigPanel>
      </div>

      <HowItWorks points={[
        { icon: Server, text: "Keys and the webhook secret live on the server. Ask whoever runs it to change them." },
        { icon: Clock, text: "Changes apply to payments started from now on; ones already started still complete." },
        { icon: KeyRound, text: "Test keys take no real money. Switch the server to live keys before going live." },
      ]} />
    </div>
  );
}

function Status({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: string; tone: Tone }) {
  const t = TONE[tone];
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border p-3">
      <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${t.tile}`}>
        <Icon className="size-5" />
      </span>
      <div className="min-w-0">
        <p className="text-xs text-muted">{label}</p>
        <p className="flex items-center gap-1.5 text-sm font-semibold">
          <span aria-hidden="true" className={`size-2 rounded-full ${t.dot}`} /> {value}
        </p>
      </div>
    </div>
  );
}
