import { redirect } from "next/navigation";
import { configTabs } from "@/components/layout/admin-nav";
import { requireStaff } from "@/lib/server/auth/session";

/** Opens the first tab the viewer may see. */
export default async function PlatformConfigPage() {
  const session = await requireStaff();
  redirect(configTabs(session.permissions)[0]?.href ?? "/admin");
}
