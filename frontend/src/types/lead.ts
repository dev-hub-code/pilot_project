/** Mirrors of the backend's Phase 9 DTOs (sales CRM). */
import type { Money } from "./marketplace";

export type LeadStage = "NEW" | "CONTACTED" | "QUALIFIED" | "PROPOSAL" | "WON" | "LOST";
export type LeadSource = "WEBSITE" | "STAFF";
export type LeadInterest = "STANDALONE" | "UNSURE";
export type ActivityType = "NOTE" | "CALL" | "EMAIL" | "MEETING" | "STAGE_CHANGE" | "ASSIGNMENT" | "SYSTEM";

export interface Lead {
  id: string;
  reference: string;
  firstName: string;
  lastName: string | null;
  email: string | null;
  phone: string | null;
  country: string | null;
  source: LeadSource;
  interest: LeadInterest;
  estimate: Money | null;
  message: string | null;
  stage: LeadStage;
  lostReason: string | null;
  ownerId: string | null;
  ownerName: string | null;
  /** The investor account this lead became, if any. */
  userId: string | null;
  won: Money | null;
  nextFollowUpAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadDetail {
  lead: Lead;
  editable: boolean;
  activities: { id: string; type: ActivityType; body: string; actorName: string | null; createdAt: string }[];
}

export interface PipelineStage {
  stage: LeadStage;
  leads: number;
  value: Money[];
}

export interface Assignee {
  userId: string;
  name: string;
  email: string;
}
