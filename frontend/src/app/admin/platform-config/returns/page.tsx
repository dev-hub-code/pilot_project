import type { Metadata } from "next";
import { EmptyState } from "@/components/ui/empty-state";
import { RentCreditCard } from "@/features/admin/rent-credit-card";
import { Permission, hasPermission } from "@/lib/permissions";
import { requireStaff } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Returns credit" };

export default async function ReturnsConfigPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.PAYOUT_PROCESS) && !can(Permission.SYSTEM_SETTINGS_MANAGE) && !can(Permission.FINANCE_VIEW)) {
    return <EmptyState title="Not available" description="This setting is for finance and admins." />;
  }
  return <RentCreditCard permissions={session.permissions} />;
}
