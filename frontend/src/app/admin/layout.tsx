import { AppShell } from "@/components/layout/app-shell";
import { requireStaff } from "@/lib/server/auth/session";

export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const session = await requireStaff();
  return <AppShell session={session} home="/admin" mainClassName="space-y-8">{children}</AppShell>;
}
