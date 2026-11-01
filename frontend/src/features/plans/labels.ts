import type { StatusTone } from "@/components/ui/status-badge";
import type { PlanAudience, ProductStatus } from "@/types/plan";

export const AUDIENCE_LABEL: Record<PlanAudience, string> = {
  ONLINE: "Online investors",
  NETWORK: "Network investors",
};

export const AUDIENCE_OPTIONS = (Object.keys(AUDIENCE_LABEL) as PlanAudience[]).map((value) => ({
  value,
  label: AUDIENCE_LABEL[value],
}));

export const PRODUCT_TONE: Record<ProductStatus, StatusTone> = {
  DRAFT: "neutral",
  OPEN: "success",
  CLOSED: "neutral",
  CANCELLED: "danger",
};

export const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = {
  DRAFT: "Draft",
  OPEN: "Open",
  CLOSED: "Closed to new investors",
  CANCELLED: "Cancelled",
};
