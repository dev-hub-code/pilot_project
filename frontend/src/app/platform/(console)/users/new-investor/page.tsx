import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { CreateInvestorForm } from "@/features/admin/investor-form";
import { Permission, hasPermission } from "@/lib/permissions";
import { requireStaff } from "@/lib/server/auth/session";
import { getPhoneCountries } from "@/lib/server/phone-countries";

export const metadata: Metadata = { title: "New investor" };

export default async function NewInvestorPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.INVESTOR_ACCOUNT_CREATE)) {
    redirect("/platform/users");
  }
  const countries = await getPhoneCountries();
  return (
    <div className="space-y-6">
      <PageHeader title="New investor"
        description="A network investor earns referral commission every month on four levels. Investors who register themselves are on the online scheme instead." />
      <Card><CreateInvestorForm countries={countries} /></Card>
    </div>
  );
}
