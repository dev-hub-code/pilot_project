import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { InviteInvestorForm } from "@/features/admin/invite-investor-form";

export const metadata: Metadata = { title: "Invite investor" };

/** Open to every employee: the admin layout already admits only staff, and the API checks again. */
export default function InviteInvestorPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Invite investor"
        description="Email a client a link to open a network investor account. Network investors earn referral commission every month on four levels." />
      <Card><InviteInvestorForm /></Card>
    </div>
  );
}
