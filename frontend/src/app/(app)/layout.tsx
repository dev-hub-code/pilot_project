import { AppHeader } from "@/components/layout/app-header";
import { requireSession } from "@/lib/server/auth/session";

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const session = await requireSession();
  return (
    <>
      <AppHeader session={session} />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-10 sm:px-6">{children}</main>
    </>
  );
}
