import type { DocumentPurpose, KycSubmission, Profile } from "@/types/user";
import { humanize } from "@/utils/format";

export const KYC_DOCUMENT_LABEL: Record<DocumentPurpose, string> = {
  KYC_AADHAAR_CARD: "Aadhaar card",
  KYC_PAN_CARD: "PAN card",
  KYC_AADHAAR_FRONT: "Aadhaar card, front",
  KYC_AADHAAR_BACK: "Aadhaar card, back",
  KYC_IDENTITY_FRONT: "Identity document, front",
  KYC_IDENTITY_BACK: "Identity document, back",
  KYC_SELFIE: "Selfie",
  KYC_PROOF_OF_ADDRESS: "Proof of address",
};

/** Personal details still to fill in before identity verification; mirrors the backend's check. */
export function missingPersonalDetails(profile: Profile): string[] {
  const blank = (value: string | null | undefined) => !value || !value.trim();
  const a = profile.address;
  return [
    blank(profile.phone) && "mobile phone",
    !profile.dateOfBirth && "date of birth",
    blank(profile.nationality) && "nationality",
    (!a || blank(a.line1) || blank(a.city) || blank(a.stateRegion) || blank(a.postalCode) || blank(a.country)) && "address",
  ].filter((item): item is string => Boolean(item));
}

/** "234567890124" -> "2345 6789 0124", as printed on the card. */
export function formatAadhaar(digits: string | null): string {
  return digits ? digits.replace(/(\d{4})(?=\d)/g, "$1 ") : "—";
}

/** The submitted identity numbers in full: "Aadhaar 2345 6789 0124 · PAN ABCDE1234F" (or an older single document). */
export function kycNumbers(s: KycSubmission): string {
  if (s.aadhaarNumber || s.panNumber) {
    return [s.aadhaarNumber && `Aadhaar ${formatAadhaar(s.aadhaarNumber)}`, s.panNumber && `PAN ${s.panNumber}`]
      .filter(Boolean).join(" · ");
  }
  return s.documentType ? `${humanize(s.documentType)} ${s.documentNumber ?? ""}`.trim() : "—";
}
