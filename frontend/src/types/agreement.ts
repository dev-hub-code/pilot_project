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
