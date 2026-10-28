/** One version of the terms and conditions. */
export interface TermsVersion {
  id: string;
  version: number;
  title: string;
  /** Null in the history list. */
  body: string | null;
  createdAt: string;
  /** Null for the wording the platform shipped with. */
  createdByName: string | null;
}

export interface TermsView {
  current: TermsVersion | null;
  /** Accounts that accepted the version in force. */
  accepted: number;
  history: TermsVersion[];
}
