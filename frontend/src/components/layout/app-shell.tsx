import { AppHeader } from "@/components/layout/app-header";
import { BackButton } from "@/components/layout/back-button";
import { navSections } from "@/components/layout/nav-sections";
import { SideNav } from "@/components/layout/side-nav";
import type { Session } from "@/lib/server/auth/session";

/** The signed-in frame: the header across the top, the menu down the left, and the page beside it. */
export function AppShell({ session, home, mainClassName = "", children }: {
  session: Session;
  /** Where the back button goes when there is no page to go back to. */
  home: string;
  mainClassName?: string;
  children: React.ReactNode;
}) {
  return (
    <>
      <AppHeader session={session} />
      <div className="flex flex-1">
        <SideNav sections={navSections(session.permissions)} />
        <div className="flex min-w-0 flex-1 flex-col">
          <BackButton home={home} />
          <main className={`mx-auto w-full max-w-7xl flex-1 px-4 pt-3 pb-10 sm:px-6 ${mainClassName}`}>{children}</main>
        </div>
      </div>
    </>
  );
}
