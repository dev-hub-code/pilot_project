import type { Assignee } from "@/types/user";
/** Mirrors of the backend's order, payment, invoice and portfolio DTOs. */
import type { Money } from "./plan";

export type OrderStatus = "PENDING_PAYMENT" | "CONFIRMED" | "EXPIRED" | "CANCELLED";

/** One plan of an order. */
export interface OrderItem {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  /** What the investor invested (returns are based on it). */
  amount: Money;
  /** The units bought; null on orders placed before units. */
  units: number | null;
  unitPrice: Money | null;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayout: Money;
  tenureMonths: number;
}

export interface Order {
  id: string;
  orderNumber: string;
  userId: string;
  status: OrderStatus;
  total: Money;
  expiresAt: string;
  confirmedAt: string | null;
  closedAt: string | null;
  closeReason: string | null;
  createdAt: string;
  items: OrderItem[];
}

/**
 * Bank payment, or cash at the office. CARD appears on payments made before card payments were withdrawn.
 */
export type PaymentMethod = "BANK_TRANSFER" | "OFFICE_CASH" | "CARD";

/** How to pay an order in cash at the office, while that payment is pending. */
export interface OfficeCashInstructions {
  /** Quoted at the counter. */
  reference: string;
  amount: Money;
  address: string;
  hours: string;
  /** The order lapses if unpaid by then. */
  payBy: string;
}
export type PaymentStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "REFUND_REQUIRED" | "REFUNDED";

/** An account the company collects investors' money in. */
export interface CompanyBankAccount {
  id: string;
  accountName: string;
  bankName: string;
  branch: string | null;
  accountNumber: string;
  ifscCode: string;
  upiId: string | null;
  active: boolean;
  createdAt: string;
}

export interface BankTransferInstructions {
  /** Quoted on the payment so finance can match it to the order. */
  reference: string;
  amount: Money;
  /** The accounts the investor may pay into. */
  accounts: CompanyBankAccount[];
}

/** CASH_DEPOSIT is no longer accepted; it appears only on earlier payments. */
export type DepositMode = "ONLINE" | "CHEQUE" | "CASH_DEPOSIT";

/** How the investor says they paid a bank payment. */
export interface DepositDetails {
  mode: DepositMode;
  reference: string;
  submittedAt: string;
  companyBankAccountId: string;
  bankName: string | null;
  accountNumber: string | null;
}

export interface Payment {
  id: string;
  orderId: string;
  orderNumber: string | null;
  userId: string;
  method: PaymentMethod;
  provider: string;
  providerReference: string;
  amount: Money;
  status: PaymentStatus;
  failureReason: string | null;
  externalReference: string | null;
  settledAt: string | null;
  refundedAt: string | null;
  refundReference: string | null;
  refundReason: string | null;
  createdAt: string;
  bankTransfer: BankTransferInstructions | null;
  deposit: DepositDetails | null;
  officeCash: OfficeCashInstructions | null;
  /** The receipt number the investor gave for cash paid at the office, once submitted. */
  receiptNumber: string | null;
  receiptSubmittedAt: string | null;
  /** Staff views: the finance member confirming it, who alone may confirm or reject it. */
  assignee?: Assignee | null;
}

export interface InvoiceParty {
  name: string;
  address: string | null;
  taxId: string | null;
  email: string | null;
}

export interface Invoice {
  id: string;
  invoiceNumber: string;
  orderId: string;
  orderNumber: string;
  issuedAt: string;
  issuer: InvoiceParty;
  buyer: InvoiceParty;
  lines: InvoiceLine[];
  total: Money;
  notes: string | null;
}

export interface InvoiceLine {
  lineNumber: number;
  description: string;
  amount: Money;
}

/** AWAITING_ACCEPTANCE: paid; the term and payouts start when the investor accepts the agreement. */
export type HoldingStatus = "AWAITING_ACCEPTANCE" | "ACTIVE" | "MATURED";

/** One investment under a plan, with its term. */
export interface Holding {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  orderId: string;
  amount: Money;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyRent: Money;
  monthlyCapitalReturn: Money;
  monthlyPayout: Money;
  tenureMonths: number;
  totalPayout: Money;
  /** ISO dates; the lease ends on leaseEndsOn (exclusive). Null until the investor accepts the lease agreement. */
  leaseStartsOn: string | null;
  leaseEndsOn: string | null;
  status: HoldingStatus;
  confirmedAt: string;
  maturedAt: string | null;
}

export interface Portfolio {
  totalsByCurrency: Money[];
  activeHoldings: number;
  holdings: Holding[];
}
