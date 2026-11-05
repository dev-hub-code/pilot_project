import { Card } from "@/components/ui/card";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { WithdrawalSettings } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { WithdrawalSettingsForm } from "./withdrawal-forms";

/** The minimum withdrawal: editable by those who generate withdrawals (admins) or manage system settings. */
export async function WithdrawalMinimumCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<WithdrawalSettings>("/api/v1/admin/settings/withdrawals");
  const minimum = formatMoney({ amount: String(settings.minimumAmount), currency: settings.currency });
  return (
    <Card title="Withdrawal settings"
      description={`The minimum (${minimum}) applies to investors' requests and to each row of a run. Charges and tax apply to investors' requests; above the threshold a second, different approver is needed.`}>
      {hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE) || hasPermission(permissions, Permission.WITHDRAWAL_PROCESS) ? (
        <WithdrawalSettingsForm settings={settings} />
      ) : (
        <p className="text-sm">{minimum} minimum · charges {settings.feeFlat > 0 ? `₹${settings.feeFlat} + ` : ""}{settings.feePercent}% · tax {settings.taxPercent}% <span className="text-muted">· only admins change these.</span></p>
      )}
      <p className="mt-2 text-xs text-muted">
        {settings.updatedAt
          ? `Last changed ${formatDateTime(settings.updatedAt)}.`
          : `The default (${formatMoney({ amount: String(settings.defaultAmount), currency: settings.currency })}) applies until it is changed here.`}
      </p>
    </Card>
  );
}
