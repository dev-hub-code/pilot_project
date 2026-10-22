"use client";

import { createContext, use, useState } from "react";

interface Plan {
  /** What "Invest now" buys: containers and lease in whole years, as calculated. */
  quantity: string;
  years: string;
  /** False while the calculator shows a problem with what was typed (nothing to invest in yet). */
  ready: boolean;
  setQuantity: (quantity: string) => void;
  setYears: (years: string) => void;
  setReady: (ready: boolean) => void;
}

const PlanContext = createContext<Plan | null>(null);

/** Links a container page's return calculator to its "Invest now" button: what the investor calculates is what they buy. */
export function InvestmentPlan({ quantity, years, children }: { quantity: string; years: string; children: React.ReactNode }) {
  const [currentQuantity, setQuantity] = useState(quantity);
  const [currentYears, setYears] = useState(years);
  const [ready, setReady] = useState(true);
  return (
    <PlanContext value={{ quantity: currentQuantity, years: currentYears, ready, setQuantity, setYears, setReady }}>
      {children}
    </PlanContext>
  );
}

/** The plan of the surrounding {@link InvestmentPlan}, or null outside one. */
export function useInvestmentPlan(): Plan | null {
  return use(PlanContext);
}
