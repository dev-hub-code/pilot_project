import { SubNav } from "@/components/layout/sub-nav";
import { PageHeader } from "@/components/ui/page-header";
import { isInvestor } from "@/lib/permissions";
import { requireSession } from "@/lib/server/auth/session";

const INVESTOR_TABS = [
  { href: "/profile", label: "Personal details" },
  { href: "/profile/verification", label: "Identity verification" },
  { href: "/profile/bank-accounts", label: "Bank accounts" },
  { href: "/change-password", label: "Password" },
] as const;

/** Staff are not investors: no identity verification or payout accounts. */
const STAFF_TABS = [
  { href: "/profile", label: "Personal details" },
  { href: "/change-password", label: "Password" },
] as const;

export default async function ProfileLayout({ children }: LayoutProps<"/profile">) {
  const investor = isInvestor((await requireSession()).permissions);
  return (
    <div className="space-y-6">
      <PageHeader title="Profile"
        description={investor ? "Your personal details, verification and payout accounts." : "Your personal details and password."} />
      <SubNav items={investor ? INVESTOR_TABS : STAFF_TABS} label="Profile sections" />
      {children}
    </div>
  );
}
