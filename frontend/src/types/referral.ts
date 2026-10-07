/** Mirrors of the backend's Phase 7 DTOs (referrals). */
import type { Money } from "./marketplace";

export interface ReferralOverview {
  code: string;
  referredBy: string | null;
  eligible: boolean;
  ineligibleReason: string | null;
  levels: { level: number; ratePercent: number; members: number }[];
  totalEarned: Money[];
  ratesEffectiveFrom: string;
}

/** Ids are opaque to the response; parentId is null for direct referrals. */
export interface DownlineMember {
  id: string;
  parentId: string | null;
  level: number;
  displayName: string;
  joinedAt: string;
  earned: Money[];
}

export interface Downline {
  members: DownlineMember[];
  truncated: boolean;
}

export interface ReferralEarning {
  id: string;
  level: number;
  sourceName: string | null;
  sourceUserId: string | null;
  beneficiaryUserId: string | null;
  productCode: string | null;
  periodNumber: number;
  base: Money;
  ratePercent: number;
  amount: Money;
  paidAt: string;
}

export type RateState = "IN_FORCE" | "SCHEDULED" | "SUPERSEDED" | "CANCELLED";

export interface RateVersion {
  id: string;
  effectiveFrom: string;
  percents: number[];
  reason: string;
  createdBy: string | null;
  createdAt: string;
  cancelledAt: string | null;
  state: RateState;
}

export interface AdminReferralView {
  code: string | null;
  uplines: { level: number; userId: string; name: string; email: string }[];
  downlineSize: number[];
  totalEarned: Money[];
}
