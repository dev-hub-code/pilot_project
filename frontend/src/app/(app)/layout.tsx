import { AppHeader } from "@/components/layout/app-header";
import { BackButton } from "@/components/layout/back-button";
import { isInvestor } from "@/lib/permissions";
import { requireSession } from "@/lib/server/auth/session";

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const session = await requireSession();
  return (
    <>
      <AppHeader session={session} />
      {/* Staff use these pages too (profile, settings); their home is the admin dashboard. */}
      <BackButton home={isInvestor(session.permissions) ? "/dashboard" : "/admin"} />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 pt-3 pb-10 sm:px-6">{children}</main>
    </>
  );
}
