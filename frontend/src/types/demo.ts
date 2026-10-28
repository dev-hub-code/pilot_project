import type { ContainerType, Money, RentTier } from "@/types/marketplace";

/** Mirrors backend DemoAccountSummary. */
export interface DemoAccountSummary {
  userId: string;
  userCode: string;
  name: string;
  email: string;
  startsOn: string;
  endsOn: string;
  orders: number;
  members: number;
}

/** Mirrors backend DemoAccountView. */
export interface DemoAccount {
  userId: string;
  userCode: string;
  name: string;
  email: string;
  startsOn: string;
  endsOn: string;
  today: string;
  /** Earnings are credited up to (not including) this day. */
  creditedUntil: string;
  /** Commission percent per level 1-4: the rates in force. */
  rates: number[];
  orders: DemoOrder[];
  members: DemoMember[];
  months: DemoMonth[];
  payouts: DemoPayout[];
  /** The plans an order can be of: open or closed. */
  plans: DemoPlan[];
  /** Monthly rent by order total, lowest first: an order earns its band's rate. */
  rentBands: RentTier[];
  totals: {
    rentPaid: Money;
    commissionPaid: Money;
    rentWithdrawn: Money;
    referralWithdrawn: Money;
    rentBalance: Money;
    referralBalance: Money;
  };
}

export interface DemoOrder {
  id: string;
  /** Null for the investor's own order. */
  memberId: string | null;
  memberName: string | null;
  orderNumber: string;
  productId: string;
  planCode: string;
  planTitle: string;
  containerType: ContainerType;
  containers: number;
  pricePerContainer: Money;
  amount: Money;
  rentPercent: number;
  monthlyRent: Money;
  tenureMonths: number;
  startsOn: string;
  endsOn: string;
  /** Rent credited so far; null for a member's order. */
  earned: Money | null;
}

export interface DemoPlan {
  id: string;
  code: string;
  title: string;
  containerType: ContainerType;
  price: Money;
  minTenureYears: number;
  maxTenureYears: number;
  status: string;
}

export interface DemoMember {
  id: string;
  parentId: string | null;
  name: string;
  joinedOn: string;
  level: number;
  /** Credited to the investor so far from this member's orders. */
  commission: Money;
}

export interface DemoMonth {
  /** yyyy-MM */
  month: string;
  rentPaid: Money;
  rentScheduled: Money;
  commission: Money;
  commissionScheduled: Money;
}

export interface DemoPayout {
  kind: "RENT" | "REFERRAL";
  /** yyyy-MM */
  earnedIn: string;
  paidOn: string;
  amount: Money;
}
