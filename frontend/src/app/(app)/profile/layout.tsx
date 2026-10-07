import { SubNav } from "@/components/layout/sub-nav";
import { PageHeader } from "@/components/ui/page-header";

const TABS = [
  { href: "/profile", label: "Personal details" },
  { href: "/profile/verification", label: "Identity verification" },
  { href: "/profile/bank-accounts", label: "Bank accounts" },
] as const;

export default function ProfileLayout({ children }: LayoutProps<"/profile">) {
  return (
    <div className="space-y-6">
      <PageHeader title="Profile" description="Your personal details, verification and payout accounts." />
      <SubNav items={TABS} label="Profile sections" />
      {children}
    </div>
  );
}
