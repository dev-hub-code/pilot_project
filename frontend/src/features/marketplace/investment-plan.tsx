"use client";

import { createContext, use, useState } from "react";

interface Plan {
  /** What "Add to cart" adds: containers and lease in whole years, as typed. */
  quantity: string;
  years: string;
  setQuantity: (quantity: string) => void;
  setYears: (years: string) => void;
}

const PlanContext = createContext<Plan | null>(null);

/**
 * Links a container page's return calculator to its "Add to cart" form: what the investor calculates is what they
 * add (they can still change it in the form).
 */
export function InvestmentPlan({ quantity, years, children }: { quantity: string; years: string; children: React.ReactNode }) {
  const [currentQuantity, setQuantity] = useState(quantity);
  const [currentYears, setYears] = useState(years);
  return (
    <PlanContext value={{ quantity: currentQuantity, years: currentYears, setQuantity, setYears }}>
      {children}
    </PlanContext>
  );
}

/** The plan of the surrounding {@link InvestmentPlan}, or null outside one. */
export function useInvestmentPlan(): Plan | null {
  return use(PlanContext);
}
