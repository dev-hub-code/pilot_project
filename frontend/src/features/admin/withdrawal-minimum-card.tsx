import { Banknote, Clock, Percent, Receipt, Settings2, UsersRound } from "lucide-react";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { WithdrawalSettings } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { ChangedNote, ConfigPanel, ConfigStat } from "./config-ui";
import { WithdrawalSettingsForm } from "./withdrawal-forms";

/** The minimum withdrawal: editable by those who generate withdrawals (admins) or manage system settings. */
export async function WithdrawalMinimumCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<WithdrawalSettings>("/api/v1/admin/settings/withdrawals");
  const money = (amount: number) => formatMoney({ amount: String(amount), currency: settings.currency });
  const canEdit = hasPermission(permissions, Permission.SYSTEM_SETTINGS_MANAGE) || hasPermission(permissions, Permission.WITHDRAWAL_PROCESS);
  return (
    <ConfigPanel icon={Settings2} title="Withdrawal settings"
      description="The minimum applies to investors' requests and to each row of a run. Charges and tax apply to investors' requests; above the threshold a second, different approver is needed.">
      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-5">
        <ConfigStat icon={Banknote} label="Minimum" value={money(settings.minimumAmount)} />
        <ConfigStat icon={Receipt} label="Charges" value={`${settings.feeFlat > 0 ? `${money(settings.feeFlat)} + ` : ""}${settings.feePercent}%`} />
        <ConfigStat icon={Percent} label="Tax (TDS)" value={`${settings.taxPercent}%`} />
        <ConfigStat icon={UsersRound} label="Second approver above" value={money(settings.dualApprovalThreshold)} />
        <ConfigStat icon={Clock} label="Processing time shown" value={settings.processingDays == null ? "Hidden" : `${settings.processingDays} working days`} />
      </div>
      {canEdit ? (
        <details className="group rounded-xl border border-border open:bg-background/30">
          <summary className="cursor-pointer list-none px-4 py-3 text-sm font-medium text-gold-text marker:hidden">
            <span className="group-open:hidden">Change settings</span><span className="hidden group-open:inline">Close</span>
          </summary>
          <div className="border-t border-border p-4"><WithdrawalSettingsForm settings={settings} /></div>
        </details>
      ) : (
        <p className="text-sm text-muted">Only admins change these.</p>
      )}
      <ChangedNote>
        {settings.updatedAt
          ? `Last changed ${formatDateTime(settings.updatedAt)}.`
          : `The default minimum (${money(settings.defaultAmount)}) applies until it is changed here.`}
      </ChangedNote>
    </ConfigPanel>
  );
}
