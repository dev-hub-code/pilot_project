import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { ProfileForm } from "@/features/profile/profile-form";
import { TaxForm } from "@/features/profile/tax-form";
import { isInvestor } from "@/lib/permissions";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import type { Profile } from "@/types/user";

export const metadata: Metadata = { title: "Profile" };

export default async function ProfilePage() {
  const [profile, session] = await Promise.all([authFetch<Profile>("/api/v1/users/me"), requireSession()]);
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <Card title="Personal details">
          <ProfileForm profile={profile} />
        </Card>
      </div>
      {isInvestor(session.permissions) && (
        <Card title="Tax information" description="Required for tax reporting on the rent you receive.">
          <TaxForm profile={profile} />
        </Card>
      )}
    </div>
  );
}
