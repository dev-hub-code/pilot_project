import type { Metadata } from "next";
import { EmptyState } from "@/components/ui/empty-state";
import { PaymentGatewayCard } from "@/features/admin/payment-gateway-card";
import { Permission, hasPermission } from "@/lib/permissions";
import { requireStaff } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Payment gateway" };

export default async function PaymentGatewayConfigPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.SYSTEM_SETTINGS_MANAGE) && !can(Permission.FINANCE_VIEW)) {
    return <EmptyState title="Not available" description="This setting is for finance and admins." />;
  }
  return <PaymentGatewayCard permissions={session.permissions} />;
}
