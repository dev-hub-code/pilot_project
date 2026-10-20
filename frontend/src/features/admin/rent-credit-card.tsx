import { Card } from "@/components/ui/card";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { RentCreditSettings } from "@/types/earning";
import { formatDateTime } from "@/utils/format";
import { RentCreditIntervalForm } from "./rent-credit-form";

/** How often rent is credited: editable by those who run payouts (admins) or manage system settings. */
export async function RentCreditCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<RentCreditSettings>("/api/v1/admin/settings/rent-credit");
  const defaultLabel = settings.options.find((o) => o.minutes === settings.defaultMinutes)?.label
    ?? `every ${settings.defaultMinutes} minutes`;
  const canEdit = hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE)
    || hasPermission(permissions, Permission.PAYOUT_PROCESS);
  return (
    <Card title="How often rent is credited"
      description="Rent accrues continuously; it reaches investors' wallets once each interval ends (intervals are counted in UTC, so a daily credit lands at midnight UTC). Changing it loses or repeats nothing: everything accrued since the last credit goes at the next boundary of the new interval. Leases still start at the full hour after acceptance.">
      {canEdit ? (
        <RentCreditIntervalForm intervalMinutes={settings.intervalMinutes} options={settings.options} />
      ) : (
        <p className="text-sm">Credited {settings.label} <span className="text-muted">· only admins change it.</span></p>
      )}
      <p className="mt-2 text-xs text-muted">
        {settings.updatedAt
          ? `Last changed ${formatDateTime(settings.updatedAt)}${settings.updatedByName ? ` by ${settings.updatedByName}` : ""}.`
          : `The default (${defaultLabel}) applies until it is changed here.`}
      </p>
    </Card>
  );
}
