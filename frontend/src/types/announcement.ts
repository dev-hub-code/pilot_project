/** Who an announcement goes to; only active accounts receive it (never suspended or disabled ones). */
export type AnnouncementAudience = "EVERYONE" | "INVESTORS" | "ONLINE_INVESTORS" | "NETWORK_INVESTORS" | "STAFF";

export const AUDIENCE_LABEL: Record<AnnouncementAudience, string> = {
  EVERYONE: "Everyone",
  INVESTORS: "All investors",
  ONLINE_INVESTORS: "Online investors",
  NETWORK_INVESTORS: "Network investors",
  STAFF: "Staff",
};

/** An announcement as sent, with how many accounts it reached. */
export interface Announcement {
  id: string;
  title: string;
  body: string;
  link: string | null;
  audience: AnnouncementAudience;
  recipients: number;
  createdAt: string;
  createdByName: string | null;
}
