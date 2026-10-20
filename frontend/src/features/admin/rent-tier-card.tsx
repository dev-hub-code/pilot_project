import { Card } from "@/components/ui/card";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { RentTiers } from "@/types/marketplace";
import { formatDateTime } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";
import { RentTierForm } from "./rent-tier-form";

/** Monthly rent by order value: editable by staff who update plans, shown to everyone else who sees plans. */
export async function RentTierCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<RentTiers>("/api/v1/admin/settings/rent-tiers");
  const canEdit = hasPermission(permissions, Permission.INVESTMENT_UPDATE);
  return (
    <div id="rent-tiers" className="scroll-mt-6">
      <Card title="Rent by order value"
        description="An order's monthly rent is its total × the % of the band its total falls in. Each band starts the rupee after the previous one ends; leave the last band's “To” blank for “and above”. Start the first band at the minimum order value. Changes apply to new orders only.">
        {canEdit ? (
          <RentTierForm tiers={settings.tiers} />
        ) : (
          <ul className="space-y-1 text-sm">
            {settings.tiers.map((t) => (
              <li key={t.fromAmount.amount} className="tabular-nums">
                {formatMoney(t.fromAmount)}{t.toAmount ? ` – ${formatMoney(t.toAmount)}` : " and above"}:
                {" "}<span className="font-medium">{formatPercent(t.monthlyRentPercent)} a month</span>
              </li>
            ))}
          </ul>
        )}
        {settings.updatedAt && (
          <p className="mt-3 text-xs text-muted">
            Last changed {formatDateTime(settings.updatedAt)}{settings.updatedByName ? ` by ${settings.updatedByName}` : ""}.
          </p>
        )}
      </Card>
    </div>
  );
}
