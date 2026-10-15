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
  /** The price of the containers, without GST. */
  amount: Money | null;
  gstRatePercent: number;
  gstAmount: Money | null;
  /** What the investor pays for the line: the price plus GST. */
  amountWithGst: Money | null;
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
  /** Everything without GST. */
  subtotal: Money | null;
  gstTotal: Money | null;
  /** What the investor pays: the subtotal plus GST. */
  total: Money | null;
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
  /** The container's price, without GST (rent is based on it). */
  amount: Money;
  gstRatePercent: number;
  gstAmount: Money;
  /** What the investor paid for the container: the price plus GST. */
  amountWithGst: Money;
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
  /** Paid in cash at the office, so without GST; {@code total} still includes GST. */
  gstWaived: boolean;
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
  /** The amount includes GST; office cash payments leave it out. */
  gstIncluded: boolean;
  /** Added to every container's monthly rent once this pays the order (office cash); 0 for none. */
  rentBonusPercent: number;
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
  /** Each line's {@code amount} is its taxable value; {@code total} adds its GST. */
  lines: InvoiceLine[];
  /** The total before GST. */
  taxable: Money;
  /** CGST + SGST within the seller's state, IGST across states. */
  cgst: Money;
  sgst: Money;
  igst: Money;
  /** The buyer's state, e.g. "Maharashtra (27)"; null when it could not be told. */
  placeOfSupply: string | null;
  total: Money;
  notes: string | null;
}

export interface InvoiceLine {
  lineNumber: number;
  description: string;
  amount: Money;
  gstRatePercent: number;
  cgst: Money;
  sgst: Money;
  igst: Money;
  total: Money;
}

/** AWAITING_CONTAINER: paid; finance has not assigned a container yet, so no lease or payouts. */
export type HoldingStatus = "AWAITING_CONTAINER" | "ACTIVE" | "MATURED";

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
  /** ISO dates; the lease ends on leaseEndsOn (exclusive). Null until a container is assigned. */
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
