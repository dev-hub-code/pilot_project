import type { Assignee } from "@/types/user";
/** Mirrors of the backend's cart, order, payment, invoice and portfolio DTOs. */
import type { ContainerSummary, ContainerType, Money, ProductStatus } from "./marketplace";

/** Containers of one plan in the cart. Payout figures cover all {@link quantity} containers. */
export interface CartLine {
  productId: string;
  productCode: string | null;
  productTitle: string;
  containerType: ContainerType | null;
  productStatus: ProductStatus | null;
  quantity: number;
  pricePerContainer: Money | null;
  /** The price of the containers: what the investor pays for the line. */
  amount: Money | null;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayout: Money | null;
  /** The lease the investor chose for these containers, within the plan's limits. */
  tenureYears: number;
  minTenureYears: number;
  maxTenureYears: number;
  /** The rent over the chosen lease. */
  totalPayout: Money | null;
  problems: string[];
}

export interface Cart {
  items: CartLine[];
  /** What the investor pays: the price of everything. */
  total: Money | null;
  /** The smallest total an order may have; zero when there is none. */
  minimumOrder: Money;
  /** Why the cart as a whole cannot be checked out (e.g. below the minimum order). */
  problems: string[];
  checkoutReady: boolean;
}

export type OrderStatus = "PENDING_PAYMENT" | "CONFIRMED" | "EXPIRED" | "CANCELLED";

/** One container of an order. Its number is shown once the order is paid. */
export interface OrderItem {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  containerType: ContainerType;
  containerNumber: string | null;
  /** The container's price: what the investor paid for it (rent is based on it). */
  amount: Money;
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

/** AWAITING_CONTAINER: paid; finance has not assigned a container yet, so no lease or payouts. */
export type HoldingStatus = "AWAITING_CONTAINER" | "AWAITING_ACCEPTANCE" | "ACTIVE" | "MATURED";

/** One container the investor owns under a plan, with its lease. */
export interface Holding {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  /** Null until finance assigns a container. */
  container: ContainerSummary | null;
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
