import { Card } from "@/components/ui/card";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { WithdrawalSettings } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { WithdrawalMinimumForm } from "./withdrawal-forms";

/** The minimum withdrawal: editable by those who generate withdrawals (admins) or manage system settings. */
export async function WithdrawalMinimumCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<WithdrawalSettings>("/api/v1/admin/settings/withdrawals");
  const minimum = formatMoney({ amount: String(settings.minimumAmount), currency: settings.currency });
  return (
    <Card title="Minimum withdrawal"
      description={`A run pays an investor's row only when it comes to at least ${minimum}; smaller amounts stay in their wallets until a later run. Changes apply from the next run.`}>
      {hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE) || hasPermission(permissions, Permission.WITHDRAWAL_PROCESS) ? (
        <WithdrawalMinimumForm minimumAmount={settings.minimumAmount} />
      ) : (
        <p className="text-sm">{minimum} <span className="text-muted">· only admins change it.</span></p>
      )}
      <p className="mt-2 text-xs text-muted">
        {settings.updatedAt
          ? `Last changed ${formatDateTime(settings.updatedAt)}.`
          : `The default (${formatMoney({ amount: String(settings.defaultAmount), currency: settings.currency })}) applies until it is changed here.`}
      </p>
    </Card>
  );
}
