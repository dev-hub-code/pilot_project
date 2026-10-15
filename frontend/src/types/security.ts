/** Mirrors of the backend's security dashboard and audit log contracts (reporting.dto). */

export type AuditCategory = "SIGN_IN" | "ACCESS_CONTROL" | "SENSITIVE_DATA" | "VERIFICATION" | "MONEY" | "OPERATIONS";

/** A person named in the log; email is null if the account no longer exists. */
export interface UserRef {
  id: string;
  email: string | null;
  name: string | null;
}

export interface AuditLogEntry {
  id: string;
  createdAt: string;
  action: string;
  category: AuditCategory;
  /** Who did it; null for anonymous visitors and the system. */
  actor: UserRef | null;
  entityType: string;
  entityId: string | null;
  /** The account the entry is about, when its entity is a user. */
  subject: UserRef | null;
  ipAddress: string | null;
  userAgent: string | null;
  correlationId: string | null;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
}

export interface SecurityOverview {
  hours: number;
  from: string;
  to: string;
  totals: {
    signIns: number;
    failedSignIns: number;
    lockouts: number;
    stolenTokenDetections: number;
    passwordResets: number;
    passwordChanges: number;
    activeSessions: number;
    signedInUsers: number;
    sensitiveAccess: number;
    privilegedChanges: number;
  };
  /** What each activity entry covers (UTC). */
  bucket: "HOUR" | "DAY";
  /** UTC start of each hour or day, as a local date-time without zone, e.g. "2026-10-13T09:00:00". */
  activity: { start: string; signIns: number; failedSignIns: number }[];
  lockedAccounts: { account: UserRef; lockedUntil: string; failedAttempts: number }[];
  failedByAccount: { account: UserRef; attempts: number; latest: string }[];
  failedByIp: { ipAddress: string; attempts: number; latest: string }[];
  sensitiveAccess: {
    staff: UserRef;
    kycDocuments: number;
    kycSubmissions: number;
    bankProofs: number;
    ticketAttachments: number;
    reports: number;
    auditExports: number;
    latest: string;
  }[];
  privilegedChanges: AuditLogEntry[];
  securityEvents: AuditLogEntry[];
}
