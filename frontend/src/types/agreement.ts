import type { Assignee } from "@/types/user";

/** One version of the lease agreement's wording. */
export interface AgreementTemplateVersion {
  id: string;
  version: number;
  title: string;
  /** Null in the history list. */
  body: string | null;
  createdAt: string;
  /** Null for the wording the platform shipped with. */
  createdByName: string | null;
}

export interface AgreementPlaceholder {
  key: string;
  description: string;
  example: string;
}

export interface AgreementTemplateView {
  current: AgreementTemplateVersion | null;
  placeholders: AgreementPlaceholder[];
  history: AgreementTemplateVersion[];
}

/** Wording filled in with example values. */
export interface AgreementPreview {
  title: string;
  body: string;
}

/** A lease agreement issued when a container was assigned. */
export interface LeaseAgreement {
  id: string;
  agreementNumber: string;
  holdingId: string;
  orderId: string;
  containerNumber: string;
  title: string;
  generatedAt: string;
  /** The lease starts only once the investor accepts it. */
  acceptanceRequired: boolean;
  /** Null until accepted, and for agreements needing no acceptance. */
  acceptedAt: string | null;
}

/** An agreement with its wording, for the investor to read before accepting it. */
export interface LeaseAgreementDocument {
  agreement: LeaseAgreement;
  body: string;
}

/** Reviewing one issued agreement: a reviewer assigns it to themselves, comments and marks it done. */
export interface AgreementReviewTask {
  id: string;
  status: "OPEN" | "DONE";
  agreement: LeaseAgreement;
  investor: { id: string; userCode: string | null; name: string; email: string };
  assignee: Assignee | null;
  /** Who marked it done, and when. */
  completedBy: Assignee | null;
  comments: number;
  createdAt: string;
}

export interface AgreementReviewComment {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  createdAt: string;
}

/** A task with the agreement's wording and its comments, oldest first. */
export interface AgreementReviewDetail {
  task: AgreementReviewTask;
  wording: string;
  comments: AgreementReviewComment[];
}
