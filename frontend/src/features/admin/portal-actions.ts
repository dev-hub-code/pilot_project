"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import type { DepartmentSummary } from "@/types/admin-portal";
import type { Role } from "@/types/auth";
import type { FormState } from "@/validators/form-state";

const path = (id: string) => encodeURIComponent(id);
const text = (formData: FormData, name: string) => String(formData.get(name) ?? "").trim();

export async function createDepartmentAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input = { code: text(formData, "code").toUpperCase(), name: text(formData, "name"), description: text(formData, "description") };
  let created: DepartmentSummary;
  try {
    created = await authFetch<DepartmentSummary>("/api/v1/admin/departments", { method: "POST", json: input });
  } catch (error) {
    return failureState(error, input);
  }
  revalidatePath("/admin/departments");
  redirect(`/admin/departments/${created.id}`);
}

async function changeDepartment(id: string, success: string, call: () => Promise<unknown>): Promise<FormState> {
  try {
    await call();
  } catch (error) {
    return failureState(error, {});
  }
  revalidatePath(`/admin/departments/${id}`);
  revalidatePath("/admin/departments");
  revalidatePath("/admin/staff", "layout");
  return { success };
}

export async function updateDepartmentAction(id: string, version: number, _p: FormState, formData: FormData): Promise<FormState> {
  const json = { name: text(formData, "name"), description: text(formData, "description"), version };
  return changeDepartment(id, "Department saved.", () => authFetch(`/api/v1/admin/departments/${path(id)}`, { method: "PUT", json }));
}

export async function setDepartmentActiveAction(id: string, active: boolean): Promise<FormState> {
  return changeDepartment(id, active ? "Department activated." : "Department deactivated.", () =>
    authFetch(`/api/v1/admin/departments/${path(id)}/${active ? "activate" : "deactivate"}`, { method: "POST" }));
}

export async function setDepartmentHeadAction(id: string, _p: FormState, formData: FormData): Promise<FormState> {
  const json = { userId: text(formData, "userId") || null };
  return changeDepartment(id, "Department head saved.", () => authFetch(`/api/v1/admin/departments/${path(id)}/head`, { method: "PUT", json }));
}

export async function assignEmployeeAction(id: string, _p: FormState, formData: FormData): Promise<FormState> {
  const json = { userId: text(formData, "userId"), primary: formData.get("primary") === "on" };
  return changeDepartment(id, "Employee assigned.", () => authFetch(`/api/v1/admin/departments/${path(id)}/employees`, { method: "POST", json }));
}

export async function removeEmployeeAction(id: string, userId: string): Promise<FormState> {
  return changeDepartment(id, "Employee removed.", () =>
    authFetch(`/api/v1/admin/departments/${path(id)}/employees/${path(userId)}`, { method: "DELETE" }));
}

/** Saves a role; a new role opens on its own page. */
export async function saveRoleAction(id: string | null, version: number, input: {
  name: string; description: string; permissions: string[]; departmentId: string | null;
}): Promise<FormState> {
  let saved: Role;
  try {
    saved = id
      ? await authFetch<Role>(`/api/v1/admin/roles/${path(id)}`, {
        method: "PUT",
        json: { description: input.description, permissions: input.permissions, departmentId: input.departmentId, version },
      })
      : await authFetch<Role>("/api/v1/admin/roles", { method: "POST", json: input });
  } catch (error) {
    return failureState(error, {});
  }
  revalidatePath("/admin/roles", "layout");
  if (!id) redirect(`/admin/roles/${saved.id}`);
  return { success: "Role saved. If its permissions changed, everyone holding it has been signed out." };
}

export async function approveRequestAction(id: string): Promise<FormState> {
  try {
    await authFetch(`/api/v1/admin/approvals/${path(id)}/approve`, { method: "POST" });
  } catch (error) {
    return failureState(error, {});
  }
  revalidatePath("/admin/approvals");
  return { success: "Approved and carried out." };
}

export async function rejectRequestAction(id: string, _p: FormState, formData: FormData): Promise<FormState> {
  const reason = text(formData, "reason");
  if (!reason) return { fieldErrors: { reason: "Say why this is rejected" } };
  try {
    await authFetch(`/api/v1/admin/approvals/${path(id)}/reject`, { method: "POST", json: { reason } });
  } catch (error) {
    return failureState(error, {});
  }
  revalidatePath("/admin/approvals");
  return { success: "Rejected." };
}
