const dateTime = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: "UTC" });
const dateOnly = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: "UTC" });

/** Timestamps are shown in UTC with an explicit suffix: financial records must be unambiguous. */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? `${dateTime.format(new Date(iso))} UTC` : "—";
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? dateOnly.format(new Date(iso)) : "—";
}

/** "PENDING_VERIFICATION" -> "Pending verification" */
export function humanize(value: string): string {
  const words = value.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}
