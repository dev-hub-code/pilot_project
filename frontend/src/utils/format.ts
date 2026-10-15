import { DISPLAY_TIME_ZONE, TIME_ZONE_LABEL } from "@/lib/time-zone";

const dateTime = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: DISPLAY_TIME_ZONE });
const dateOnly = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: DISPLAY_TIME_ZONE });
const calendarDate = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: "UTC" });

/** Timestamps are shown in the display time zone with its name after them: financial records must be unambiguous. */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? `${dateTime.format(new Date(iso))} ${TIME_ZONE_LABEL}` : "—";
}

/** The date of a timestamp in the display time zone; a plain date ("2026-10-14") as it is. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return /^\d{4}-\d{2}-\d{2}$/.test(iso) ? calendarDate.format(new Date(`${iso}T00:00:00Z`)) : dateOnly.format(new Date(iso));
}

/** Today, in the viewer's own time zone, as YYYY-MM-DD: the latest date a date-of-birth calendar offers. */
export function latestBirthDate(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

/** "1985-06-15" -> "15/06/1985": how dates of birth are entered and shown. */
export function formatDayMonthYear(iso: string | null | undefined): string {
  const match = iso ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso) : null;
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "";
}

/**
 * "15/06/1985" (or 5/6/1985, 15-06-1985, 15.06.1985, 15061985) -> "1985-06-15"; null when it is not a real date in that
 * day-month-year order.
 */
export function parseDayMonthYear(text: string): string | null {
  const value = text.trim();
  const match = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(value) ?? /^(\d{2})(\d{2})(\d{4})$/.exec(value);
  const [, day, month, year] = match ?? [];
  if (!day || !month || !year) return null;
  const iso = `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
  const date = new Date(`${iso}T00:00:00Z`);
  // Rejects dates such as 31/02, which Date would roll over into March.
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === iso ? iso : null;
}

const monthYear = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });

/** "2026-10" -> "October 2026" */
export function formatMonth(month: string): string {
  return monthYear.format(new Date(`${month}-01T00:00:00Z`));
}

/** 10 -> "10th", 31 -> "31st" */
export function ordinal(n: number): string {
  const teen = n % 100 >= 11 && n % 100 <= 13;
  const suffix = teen ? "th" : ({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th";
  return `${n}${suffix}`;
}

const dayMonth = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });

/** A payout's period, e.g. "5 Oct – 10 Oct". */
export function formatPeriod(start: string, end: string): string {
  return `${dayMonth.format(new Date(start))} – ${dayMonth.format(new Date(end))}`;
}

/** "PENDING_VERIFICATION" -> "Pending verification" */
export function humanize(value: string): string {
  const words = value.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}
