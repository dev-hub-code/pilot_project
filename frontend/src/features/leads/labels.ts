import type { StatusTone } from "@/components/ui/status-badge";
import type { ActivityType, LeadInterest, LeadStage } from "@/types/lead";

export const STAGES: readonly LeadStage[] = ["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "WON", "LOST"];

export const STAGE_LABEL: Record<LeadStage, string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  PROPOSAL: "Proposal",
  WON: "Won",
  LOST: "Lost",
};

export const STAGE_TONE: Record<LeadStage, StatusTone> = {
  NEW: "warning",
  CONTACTED: "neutral",
  QUALIFIED: "neutral",
  PROPOSAL: "neutral",
  WON: "success",
  LOST: "danger",
};

export const INTEREST_LABEL: Record<LeadInterest, string> = {
  STANDALONE: "Buying containers",
  UNSURE: "Not sure yet",
};

export const ACTIVITY_LABEL: Record<ActivityType, string> = {
  NOTE: "Note",
  CALL: "Call",
  EMAIL: "Email",
  MEETING: "Meeting",
  STAGE_CHANGE: "Stage",
  ASSIGNMENT: "Assignment",
  SYSTEM: "System",
};
