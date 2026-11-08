import {
  BarChart3, Briefcase, Handshake, Headset, Landmark, type LucideIcon, Settings, ShieldCheck, Target, Users,
} from "lucide-react";
import type { Role } from "@/types/auth";

/** Each department's icon, by its code; others fall back to a building. */
export const DEPARTMENT_ICON: Record<string, LucideIcon> = {
  PLATFORM_ADMIN: Settings,
  INVESTOR_RELATIONS: Users,
  INVESTMENT_OPS: Briefcase,
  FINANCE: Landmark,
  COMPLIANCE: ShieldCheck,
  CUSTOMER_SUPPORT: Headset,
  SALES_CRM: Target,
  PARTNER_OPS: Handshake,
  BUSINESS_INTELLIGENCE: BarChart3,
};

/** "PAYMENT_EXECUTIVE" → "Payment executive". */
export function readableCode(name: string): string {
  const words = name.toLowerCase().split("_");
  return words.map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w)).join(" ");
}

/** A role's display name: its description when it reads as a name, else its code made readable. */
export function roleTitle(role: Pick<Role, "name" | "description">): string {
  const d = role.description?.trim();
  return d && d.length <= 60 ? d.charAt(0).toUpperCase() + d.slice(1) : readableCode(role.name);
}
