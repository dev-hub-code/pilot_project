import type { BankProofType } from "@/types/user";

export const BANK_PROOF_LABEL: Record<BankProofType, string> = {
  BANK_STATEMENT: "Bank statement",
  CANCELLED_CHEQUE: "Cancelled cheque",
};
