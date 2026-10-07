import type { Metadata } from "next";
import { Notice } from "@/components/ui/notice";
import { ChangePasswordForm } from "@/features/auth/change-password-form";

export const metadata: Metadata = { title: "Password" };

export default async function PasswordSettingsPage({ searchParams }: PageProps<"/settings/password">) {
  const changed = (await searchParams).changed === "1";
  return (
    <div className="space-y-4">
      {changed && <Notice tone="success">Password changed. Other devices have been signed out.</Notice>}
      <p className="text-sm text-muted">Use a long passphrase you don&apos;t use anywhere else.</p>
      <ChangePasswordForm temporary={false} returnTo="/settings/password?changed=1" />
    </div>
  );
}
