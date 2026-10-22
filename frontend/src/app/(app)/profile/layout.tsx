import { SubNav } from "@/components/layout/sub-nav";
import { OnboardingSteps } from "@/features/profile/onboarding-steps";
import { PageHeader } from "@/components/ui/page-header";
import { isInvestor } from "@/lib/permissions";
import { requireSession } from "@/lib/server/auth/session";
import { getOnboarding } from "@/lib/server/onboarding";

const INVESTOR_TABS = [
  { href: "/profile", label: "Personal details" },
  { href: "/profile/verification", label: "Identity verification" },
  { href: "/profile/bank-accounts", label: "Bank accounts" },
] as const;

export default async function ProfileLayout({ children }: LayoutProps<"/profile">) {
  const investor = isInvestor((await requireSession()).permissions);
  // Until an investor has set up their account, the tabs become its steps, in the order they must be done.
  const onboarding = investor ? await getOnboarding() : null;
  return (
    <div className="space-y-6">
      <PageHeader title="Profile"
        description={investor ? "Your personal details, verification and payout accounts." : "Your personal details."} />
      {/* Staff are not investors: no identity verification or payout accounts, so no tabs. */}
      {onboarding?.next ? <OnboardingSteps steps={onboarding.steps} />
        : investor && <SubNav items={INVESTOR_TABS} label="Profile sections" />}
      {children}
    </div>
  );
}
