/** Mirrors of the backend's Phase 5 DTOs (cart, order, payment, invoice and portfolio). */
import type { InvestmentType, Money, ProductStatus, RentalFrequency } from "./marketplace";

export interface CartLine {
  productId: string;
  productCode: string | null;
  productTitle: string;
  investmentType: InvestmentType | null;
  productStatus: ProductStatus | null;
  amount: Money;
  ownershipPercent: number;
  rentalPerPayment: Money | null;
  rentalFrequency: RentalFrequency | null;
  durationMonths: number;
  termsVersion: string | null;
  problems: string[];
}

export interface Cart {
  items: CartLine[];
  total: Money | null;
  checkoutReady: boolean;
}

export type OrderStatus = "PENDING_PAYMENT" | "CONFIRMED" | "EXPIRED" | "CANCELLED";

export interface OrderItem {
  productId: string;
  productCode: string;
  productTitle: string;
  investmentType: InvestmentType;
  amount: Money;
  ownershipPercent: number;
  rentalPerPayment: Money;
  rentalFrequency: RentalFrequency;
  durationMonths: number;
  termsVersion: string;
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

export type PaymentMethod = "BANK_TRANSFER" | "CARD";
export type PaymentStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "REFUND_REQUIRED" | "REFUNDED";

export interface BankTransferInstructions {
  beneficiaryName: string;
  iban: string;
  bic: string;
  bankName: string;
  reference: string;
  amount: Money;
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
  simulated: boolean;
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

export type HoldingStatus = "ACTIVE" | "MATURED" | "CLOSED";

export interface Holding {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  investmentType: InvestmentType;
  productStatus: ProductStatus;
  orderId: string;
  amount: Money;
  ownershipPercent: number;
  expectedRentalPerPayment: Money;
  rentalFrequency: RentalFrequency;
  durationMonths: number;
  termsVersion: string;
  status: HoldingStatus;
  confirmedAt: string;
}

export interface Portfolio {
  totalsByCurrency: Money[];
  activeHoldings: number;
  holdings: Holding[];
}
