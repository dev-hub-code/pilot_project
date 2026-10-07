import type { StatusTone } from "@/components/ui/status-badge";
import type { ContainerCondition, ContainerType, ProductStatus, RiskLevel } from "@/types/marketplace";

export const CONTAINER_TYPE_LABEL: Record<ContainerType, string> = {
  DRY_20FT: "20ft Dry",
  DRY_40FT: "40ft Dry",
  HIGH_CUBE_40FT: "40ft High Cube",
  HIGH_CUBE_45FT: "45ft High Cube",
  REEFER_20FT: "20ft Reefer",
  REEFER_40FT: "40ft Reefer",
  OPEN_TOP_20FT: "20ft Open Top",
  OPEN_TOP_40FT: "40ft Open Top",
  FLAT_RACK_20FT: "20ft Flat Rack",
  FLAT_RACK_40FT: "40ft Flat Rack",
  TANK_20FT: "20ft Tank",
};

export const CONDITION_LABEL: Record<ContainerCondition, string> = {
  NEW: "New (one-trip)",
  CARGO_WORTHY: "Cargo-worthy",
  WIND_WATERTIGHT: "Wind & watertight",
};

export const RISK_LABEL: Record<RiskLevel, string> = { LOW: "Low risk", MEDIUM: "Medium risk", HIGH: "High risk" };

export const RISK_TONE = { LOW: "success", MEDIUM: "warning", HIGH: "danger" } as const;

export const CONTAINER_TYPE_OPTIONS = (Object.keys(CONTAINER_TYPE_LABEL) as ContainerType[]).map((value) => ({
  value,
  label: CONTAINER_TYPE_LABEL[value],
}));

export const PRODUCT_TONE: Record<ProductStatus, StatusTone> = {
  DRAFT: "neutral",
  OPEN: "success",
  FUNDED: "success",
  ACTIVE: "success",
  MATURED: "neutral",
  CLOSED: "neutral",
  CANCELLED: "danger",
};
