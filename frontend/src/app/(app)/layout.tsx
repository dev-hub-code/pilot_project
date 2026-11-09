import { AppShell } from "@/components/layout/app-shell";
import { isInvestor } from "@/lib/permissions";
import { requireSession } from "@/lib/server/auth/session";

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const session = await requireSession();
  // Staff use these pages too (profile, settings); their home is the admin dashboard.
  return <AppShell session={session} home={isInvestor(session.permissions) ? "/dashboard" : "/platform"}>{children}</AppShell>;
}
