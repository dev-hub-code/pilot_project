import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { ProfileForm } from "@/features/profile/profile-form";
import { authFetch } from "@/lib/server/auth/session";
import { backendFetch } from "@/lib/server/backend-client";
import { requireEmailVerified } from "@/lib/server/onboarding";
import type { Country } from "@/types/location";
import type { Profile } from "@/types/user";

export const metadata: Metadata = { title: "Profile" };

export default async function ProfilePage({ searchParams }: PageProps<"/profile">) {
  await requireEmailVerified();
  const fromEmail = (await searchParams).step === "email";
  const [profile, countries] = await Promise.all([
    authFetch<Profile>("/api/v1/users/me"),
    backendFetch<Country[]>("/api/v1/public/locations/countries"),
  ]);
  return (
    <div className="space-y-6">
      {fromEmail && <Notice tone="success">Your email address is confirmed. Next, fill in your personal details.</Notice>}
      <Card title="Personal details">
        <ProfileForm profile={profile} countries={countries} />
      </Card>
    </div>
  );
}
