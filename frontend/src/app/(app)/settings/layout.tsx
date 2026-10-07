import { SubNav } from "@/components/layout/sub-nav";
import { PageHeader } from "@/components/ui/page-header";

const TABS = [
  { href: "/settings", label: "Appearance" },
  { href: "/settings/notifications", label: "Notifications" },
  { href: "/settings/password", label: "Password" },
] as const;

export default function SettingsLayout({ children }: LayoutProps<"/settings">) {
  return (
    <div className="space-y-6">
      <PageHeader title="Settings" description="How SeaLease looks, how it contacts you, and your sign-in." />
      <SubNav items={TABS} label="Settings sections" />
      <div className="max-w-2xl">{children}</div>
    </div>
  );
}
