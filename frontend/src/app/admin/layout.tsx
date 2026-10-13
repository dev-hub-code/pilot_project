import { AppHeader } from "@/components/layout/app-header";
import { requireStaff } from "@/lib/server/auth/session";

export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const session = await requireStaff();
  // The admin pages are listed in the account menu (the avatar), not as tabs here.
  return (
    <>
      <AppHeader session={session} />
      <main className="mx-auto w-full max-w-7xl flex-1 space-y-8 px-4 py-10 sm:px-6">{children}</main>
    </>
  );
}
