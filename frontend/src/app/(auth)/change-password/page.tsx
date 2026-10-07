import type { Metadata } from "next";
import { ChangePasswordForm } from "@/features/auth/change-password-form";
import { requireSession } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Change password" };

export default async function ChangePasswordPage() {
  const session = await requireSession();
  const temporary = session.passwordChangeRequired;
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">{temporary ? "Choose your password" : "Change password"}</h1>
        <p className="text-sm text-muted">
          {temporary
            ? "You signed in with a temporary password. Replace it with one only you know to continue."
            : "Use a long passphrase you don't use anywhere else."}
        </p>
      </div>
      <ChangePasswordForm temporary={temporary} />
    </div>
  );
}
