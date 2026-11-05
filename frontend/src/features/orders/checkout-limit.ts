import type { Money } from "@/types/plan";

/** Whether `amount` is over the online checkout's limit. Only a hint: the backend refuses such a checkout itself. */
export function overCheckoutLimit(amount: Money, limit: Money | undefined): boolean {
  return limit != null && Number(amount.amount) > Number(limit.amount);
}
