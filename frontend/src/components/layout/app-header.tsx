import { Logo } from "@/components/brand/logo";
import { AccountMenu } from "@/components/layout/account-menu";
import { MobileNav } from "@/components/layout/mobile-nav";
import { navSections } from "@/components/layout/nav-sections";
import { NotificationBell } from "@/components/layout/notification-bell";
import { isInvestor } from "@/lib/permissions";
import { authFetch, type Session } from "@/lib/server/auth/session";
import { isDemoAccount } from "@/lib/server/demo";
import type { Profile } from "@/types/user";

export async function AppHeader({ session }: { session: Session }) {
  // The badge is a convenience: a failure here must never break the page.
  const [unread, me] = await Promise.all([
    authFetch<{ unread: number }>("/api/v1/notifications/unread-count").then((r) => r.unread, () => 0),
    authFetch<Profile>("/api/v1/users/me").then((p) => p, () => null),
  ]);
  const name = me ? `${me.firstName} ${me.lastName}`.trim() || me.email : "Account";
  const investor = isInvestor(session.permissions);
  const demo = investor && await isDemoAccount();

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface print:hidden">
      <div className="flex h-20 w-full items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-2">
          <MobileNav sections={navSections(session.permissions)} />
          <Logo href={investor ? "/dashboard" : "/platform"} compact />
        </div>
        <div className="flex items-center gap-4">
          <NotificationBell initial={unread} />
          <AccountMenu name={name} email={me?.email ?? null} userCode={me?.userCode ?? null} demo={demo} />
        </div>
      </div>
    </header>
  );
}
