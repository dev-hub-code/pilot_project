/**
 * The time zone dates and times are shown in: the business's own (India Standard Time by default), the same on the
 * server and in the browser so pages render alike. Data is kept in UTC; this is only how it is shown.
 */
export const DISPLAY_TIME_ZONE = process.env.NEXT_PUBLIC_DISPLAY_TIME_ZONE || "Asia/Kolkata";

/** "IST": the zone's short name, shown after times. */
export const TIME_ZONE_LABEL = new Intl.DateTimeFormat("en-IN", { timeZone: DISPLAY_TIME_ZONE, timeZoneName: "short" })
  .formatToParts(new Date())
  .find((part) => part.type === "timeZoneName")?.value ?? DISPLAY_TIME_ZONE;

/** Today in the display time zone, as YYYY-MM-DD. */
export function todayInZone(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: DISPLAY_TIME_ZONE }).format(new Date());
}

/** The display zone's offset from UTC at {@code at}, in minutes (330 for India Standard Time). */
function offsetMinutes(at: Date): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: DISPLAY_TIME_ZONE, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit",
  }).formatToParts(at);
  const n = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const asUtc = Date.UTC(n("year"), n("month") - 1, n("day"), n("hour"), n("minute"), n("second"));
  return Math.round((asUtc - at.getTime()) / 60000);
}

/** A datetime-local value ("2026-10-14T15:30"), read as display-zone time, as a UTC ISO instant. */
export function zonedInputToIso(local: string): string {
  const guess = new Date(`${local.length === 16 ? `${local}:00` : local}Z`);
  const first = new Date(guess.getTime() - offsetMinutes(guess) * 60000);
  // Once more, in case the guess and the answer fall either side of a daylight-saving change.
  return new Date(guess.getTime() - offsetMinutes(first) * 60000).toISOString();
}

/** A UTC ISO instant as a datetime-local value in the display zone ("2026-10-14T15:30"). */
export function isoToZonedInput(iso: string | null | undefined): string | undefined {
  if (!iso) return undefined;
  const at = new Date(iso);
  return new Date(at.getTime() + offsetMinutes(at) * 60000).toISOString().slice(0, 16);
}
