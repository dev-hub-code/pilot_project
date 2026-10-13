import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { ProfileForm } from "@/features/profile/profile-form";
import { authFetch } from "@/lib/server/auth/session";
import type { Profile } from "@/types/user";

export const metadata: Metadata = { title: "Profile" };

export default async function ProfilePage() {
  const profile = await authFetch<Profile>("/api/v1/users/me");
  return (
    <Card title="Personal details">
      <ProfileForm profile={profile} />
    </Card>
  );
}
