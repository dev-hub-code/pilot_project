/** Mirrors of the backend's Phase 3 DTOs (user, kyc and bankaccount modules). */

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DISABLED";
export type KycStatus = "NOT_SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED";

export interface Address {
  line1: string | null;
  line2: string | null;
  city: string | null;
  stateRegion: string | null;
  postalCode: string | null;
  country: string | null;
}

export interface Profile {
  id: string;
  /** The readable ID ("SL03500"). */
  userCode: string;
  email: string;
  firstName: string;
  lastName: string;
  status: UserStatus;
  /** In international form, e.g. "+919876543210". */
  phone: string | null;
  /** The phone number's country (ISO 3166-1 alpha-2); null for a number saved before countries were asked. */
  phoneCountry: string | null;
  dateOfBirth: string | null;
  nationality: string | null;
  address: Address;
  taxResidencyCountry: string | null;
  taxIdMasked: string | null;
  kycStatus: KycStatus;
  emailNotifications: boolean;
  smsNotifications: boolean;
  twoFactorEnabled: boolean;
}

export type IdentityDocumentType = "PASSPORT" | "NATIONAL_ID" | "DRIVING_LICENSE";
/** DRAFT while only one of Aadhaar and PAN is uploaded; PENDING (under review) once both are in. */
export type KycSubmissionStatus = "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";
export type DocumentPurpose =
  | "KYC_AADHAAR_CARD" | "KYC_PAN_CARD"
  // Earlier submissions: Aadhaar as two files, or one identity document with selfie and proof of address.
  | "KYC_AADHAAR_FRONT" | "KYC_AADHAAR_BACK"
  | "KYC_IDENTITY_FRONT" | "KYC_IDENTITY_BACK" | "KYC_SELFIE" | "KYC_PROOF_OF_ADDRESS";

/** The reviewer a KYC review or bank-account verification is assigned to; only they may decide it. */
export interface Assignee {
  id: string;
  name: string;
  assignedAt: string;
}

export interface KycSubmission {
  id: string;
  userId: string;
  status: KycSubmissionStatus;
  legalFirstName: string;
  legalLastName: string;
  dateOfBirth: string;
  nationality: string;
  /** The full 12 digits; null on submissions made before Aadhaar and PAN. */
  aadhaarNumber: string | null;
  /** e.g. "ABCDE1234F". */
  panNumber: string | null;
  /** Submissions made before Aadhaar and PAN carried one identity document instead. */
  documentType: IdentityDocumentType | null;
  documentNumber: string | null;
  documentIssuingCountry: string | null;
  documentExpiryDate: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
  rejectionReason: string | null;
  /** Null in the investor's own view, and while nobody has taken the review. */
  assignee: Assignee | null;
  documents: { id: string; purpose: DocumentPurpose }[];
}

export interface UserAccount {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: UserStatus;
}

export interface KycReviewDetail {
  submission: KycSubmission;
  account: UserAccount;
}

export type BankAccountStatus = "PENDING_VERIFICATION" | "VERIFIED" | "REJECTED" | "REMOVED";

export type BankProofType = "BANK_STATEMENT" | "CANCELLED_CHEQUE";

export interface BankAccount {
  id: string;
  accountHolderName: string;
  bankName: string;
  country: string;
  currency: string;
  accountNumberMasked: string;
  status: BankAccountStatus;
  primary: boolean;
  rejectionReason: string | null;
  createdAt: string;
  verifiedAt: string | null;
  /** Set when this account was submitted as a change of another account. */
  replacesAccountId: string | null;
  /** The proof uploaded with it; null for accounts added before proof was required. */
  proofType: BankProofType | null;
}

export interface AdminBankAccount {
  userId: string;
  account: BankAccount;
  reviewedBy: string | null;
  otherUsersWithSameAccount: number;
  /** The account a change request would retire once verified. */
  replaces: BankAccount | null;
  assignee: Assignee | null;
}

export interface AdminUserSummary {
  id: string;
  /** The readable ID ("SL03500"). */
  userCode: string;
  email: string;
  firstName: string;
  lastName: string;
  status: UserStatus;
  kycStatus: KycStatus;
  /** When the user confirmed their email address; null while it is unverified. */
  emailVerifiedAt: string | null;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AdminUserDetail {
  summary: AdminUserSummary;
  profile: Profile;
  statusReason: string | null;
  statusChangedAt: string | null;
}
