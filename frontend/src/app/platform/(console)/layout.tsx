import { AppShell } from "@/components/layout/app-shell";
import { requireStaff } from "@/lib/server/auth/session";

export default async function AdminLayout({ children }: LayoutProps<"/platform">) {
  const session = await requireStaff();
  return <AppShell session={session} home="/platform" mainClassName="space-y-8">{children}</AppShell>;
}
