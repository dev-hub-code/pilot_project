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
  amount: Money | null;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayout: Money | null;
  tenureMonths: number;
  totalPayout: Money | null;
  problems: string[];
}

export interface Cart {
  items: CartLine[];
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

/** Only bank payment is offered; CARD appears on payments made before card payments were withdrawn. */
export type PaymentMethod = "BANK_TRANSFER" | "CARD";
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
  lines: { lineNumber: number; description: string; amount: Money }[];
  total: Money;
  notes: string | null;
}

export type HoldingStatus = "ACTIVE" | "MATURED";

/** One container the investor owns under a plan, with its lease. */
export interface Holding {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  container: ContainerSummary;
  orderId: string;
  amount: Money;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyRent: Money;
  monthlyCapitalReturn: Money;
  monthlyPayout: Money;
  tenureMonths: number;
  totalPayout: Money;
  /** ISO dates; the lease ends on leaseEndsOn (exclusive). */
  leaseStartsOn: string;
  leaseEndsOn: string;
  status: HoldingStatus;
  confirmedAt: string;
  maturedAt: string | null;
}

export interface Portfolio {
  totalsByCurrency: Money[];
  activeHoldings: number;
  holdings: Holding[];
}
