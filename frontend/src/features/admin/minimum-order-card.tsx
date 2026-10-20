import { Card } from "@/components/ui/card";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { MinimumOrder } from "@/types/marketplace";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { MinimumOrderForm } from "./minimum-order-form";

/** The minimum order value: editable by staff who update plans, shown to everyone else who sees plans. */
export async function MinimumOrderCard({ permissions }: { permissions: readonly string[] }) {
  const settings = await authFetch<MinimumOrder>("/api/v1/admin/settings/minimum-order");
  const canEdit = hasPermission(permissions, Permission.INVESTMENT_UPDATE);
  const none = Number(settings.amount.amount) === 0;
  return (
    <div id="minimum-order" className="scroll-mt-6">
      <Card title="Minimum order value"
        description="The smallest total an investor's order may have, across every container in their cart. Below it the cart cannot be checked out. Enter 0 for no minimum. Orders already placed are not affected.">
        {canEdit ? (
          <MinimumOrderForm amount={settings.amount.amount} />
        ) : (
          <p className="text-sm">{none ? "No minimum" : formatMoney(settings.amount)} <span className="text-muted">· only listing managers change it.</span></p>
        )}
        {settings.updatedAt && (
          <p className="mt-2 text-xs text-muted">
            Last changed {formatDateTime(settings.updatedAt)}{settings.updatedByName ? ` by ${settings.updatedByName}` : ""}.
          </p>
        )}
      </Card>
    </div>
  );
}
