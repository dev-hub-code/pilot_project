import type { StatusTone } from "./status-badge";

/** One place mapping every domain status to a badge colour. */
const TONES: Record<string, StatusTone> = {
  ACTIVE: "success",
  APPROVED: "success",
  VERIFIED: "success",
  PENDING: "warning",
  PENDING_VERIFICATION: "warning",
  NOT_SUBMITTED: "neutral",
  SUSPENDED: "danger",
  DISABLED: "danger",
  REJECTED: "danger",
  REMOVED: "neutral",
  RETAIL: "neutral",
  HNI: "success",
  MATURED: "neutral",
  RECORDED: "warning",
  DISTRIBUTED: "success",
  IN_FORCE: "success",
  SCHEDULED: "warning",
  SUPERSEDED: "neutral",
  CANCELLED: "neutral",
  CLOSED: "neutral",
};

export function toneFor(status: string): StatusTone {
  return TONES[status] ?? "neutral";
}
