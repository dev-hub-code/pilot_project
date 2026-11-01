import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { PageHeader } from "@/components/ui/page-header";
import { BankAccountQueue } from "@/features/admin/bank-account-queue";
import { KycQueue } from "@/features/admin/kyc-queue";
import { Permission, hasPermission } from "@/lib/permissions";
import { requireStaff } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Verification" };

/** KYC reviews, each with the investor's bank accounts. Staff who cannot open those reviews get the bank accounts alone. */
export default async function VerificationPage({ searchParams }: PageProps<"/admin/verification">) {
  const params = await searchParams;
  const session = await requireStaff();
  const canKyc = hasPermission(session.permissions, Permission.KYC_REVIEW);
  const canBank = hasPermission(session.permissions, Permission.BANK_ACCOUNT_VERIFY);
  if (!canKyc && !canBank) redirect("/admin");
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);

  return (
    <div className="space-y-6">
      <PageHeader title="Verification" description="Check investors' identity and bank accounts before they invest and are paid." />
      {canKyc ? <KycQueue statusParam={params.status} page={page} /> : <BankAccountQueue page={page} />}
    </div>
  );
}
