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
  email: string;
  firstName: string;
  lastName: string;
  status: UserStatus;
  phone: string | null;
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
export type KycSubmissionStatus = "PENDING" | "APPROVED" | "REJECTED";
export type DocumentPurpose = "KYC_IDENTITY_FRONT" | "KYC_IDENTITY_BACK" | "KYC_SELFIE" | "KYC_PROOF_OF_ADDRESS";

export interface KycSubmission {
  id: string;
  userId: string;
  status: KycSubmissionStatus;
  legalFirstName: string;
  legalLastName: string;
  dateOfBirth: string;
  nationality: string;
  documentType: IdentityDocumentType;
  documentNumberMasked: string;
  documentIssuingCountry: string;
  documentExpiryDate: string;
  submittedAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
  rejectionReason: string | null;
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
}

export interface AdminBankAccount {
  userId: string;
  account: BankAccount;
  reviewedBy: string | null;
  otherUsersWithSameAccount: number;
}

export interface AdminUserSummary {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: UserStatus;
  kycStatus: KycStatus;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AdminUserDetail {
  summary: AdminUserSummary;
  profile: Profile;
  statusReason: string | null;
  statusChangedAt: string | null;
}
