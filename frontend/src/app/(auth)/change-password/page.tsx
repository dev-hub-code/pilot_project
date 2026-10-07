import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { ChangePasswordForm } from "@/features/auth/change-password-form";
import { requireSession } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Choose your password" };

export default async function ChangePasswordPage() {
  const session = await requireSession();
  const temporary = session.passwordChangeRequired;
  // A voluntary change lives in Settings; this page is for replacing a temporary password.
  if (!temporary) redirect("/settings/password");
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Choose your password</h1>
        <p className="text-sm text-muted">
          You signed in with a temporary password. Replace it with one only you know to continue.
        </p>
      </div>
      <ChangePasswordForm temporary />
    </div>
  );
}
