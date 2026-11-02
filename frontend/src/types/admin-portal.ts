export interface DepartmentSummary {
  id: string;
  code: string;
  name: string;
  description: string;
  active: boolean;
  headUserId: string | null;
  headName: string | null;
  employees: number;
  roles: number;
  version: number;
}

export interface DepartmentDetail {
  department: DepartmentSummary;
  employees: { userId: string; name: string; email: string; primary: boolean; head: boolean; assignedAt: string }[];
  roles: { id: string; name: string; description: string; employees: number }[];
  history: { action: string; detail: string; actorName: string | null; at: string }[];
}

export interface StaffMember {
  id: string;
  name: string;
  email: string;
  status: string;
  roles: string[];
  departments: { id: string; code: string; name: string; primary: boolean }[];
}

export interface PermissionEntry {
  code: string;
  category: string;
  description: string;
  sensitive: boolean;
  reserved: boolean;
}

export interface RoleRevision {
  revision: number;
  description: string;
  permissions: string[];
  changedBy: string | null;
  changedAt: string;
}

export interface Approval {
  id: string;
  type: "LEDGER_ADJUSTMENT" | "PAYMENT_REFUND";
  status: "PENDING" | "APPROVED" | "REJECTED";
  summary: string;
  reason: string;
  createdBy: string;
  assignedTo: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  transactionReference: string | null;
  createdAt: string;
}

export interface DashboardSection {
  key: string;
  title: string;
  metrics: { label: string; value: number; href: string }[];
}
