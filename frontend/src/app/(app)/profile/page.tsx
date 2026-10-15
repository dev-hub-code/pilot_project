import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { ProfileForm } from "@/features/profile/profile-form";
import { authFetch } from "@/lib/server/auth/session";
import { backendFetch } from "@/lib/server/backend-client";
import type { Country } from "@/types/location";
import type { Profile } from "@/types/user";

export const metadata: Metadata = { title: "Profile" };

export default async function ProfilePage() {
  const [profile, countries] = await Promise.all([
    authFetch<Profile>("/api/v1/users/me"),
    backendFetch<Country[]>("/api/v1/public/locations/countries"),
  ]);
  return (
    <Card title="Personal details">
      <ProfileForm profile={profile} countries={countries} />
    </Card>
  );
}
