import type { Metadata } from "next";
import { NotificationsForm } from "@/features/settings/notifications-form";
import { authFetch } from "@/lib/server/auth/session";
import type { Profile } from "@/types/user";

export const metadata: Metadata = { title: "Notifications" };

export default async function NotificationSettingsPage() {
  const profile = await authFetch<Profile>("/api/v1/users/me");
  return <NotificationsForm email={profile.emailNotifications} sms={profile.smsNotifications} />;
}
