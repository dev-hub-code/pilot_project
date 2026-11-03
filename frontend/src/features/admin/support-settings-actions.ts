"use server";

import { revalidatePath } from "next/cache";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import type { FormState } from "@/validators/form-state";

const BASE = "/api/v1/admin/support/config";
const UUID = /^[0-9a-f-]{36}$/i;

const text = (f: FormData, k: string) => String(f.get(k) ?? "").trim();
const optional = (f: FormData, k: string) => text(f, k) || null;
const int = (f: FormData, k: string) => (text(f, k) === "" ? null : Number.parseInt(text(f, k), 10));
const on = (f: FormData, k: string) => f.get(k) === "on";
const values = (f: FormData) => Object.fromEntries([...f.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;

async function send(path: string, method: "POST" | "PUT", json: unknown, f: FormData, done: string): Promise<FormState> {
  try {
    await authFetch(`${BASE}${path}`, { method, json });
  } catch (error) {
    return failureState(error, values(f));
  }
  revalidatePath("/admin/support/settings");
  return { success: done };
}

/** Hours (number) to minutes, as the API counts targets in minutes. */
function minutes(f: FormData, k: string): number | null {
  const v = text(f, k);
  if (v === "") return null;
  const hours = Number(v);
  return Number.isFinite(hours) ? Math.round(hours * 60) : Number.NaN;
}

export async function savePolicyAction(id: string | null, _p: FormState, f: FormData): Promise<FormState> {
  const first = minutes(f, "firstResponseHours");
  const resolution = minutes(f, "resolutionHours");
  const next = minutes(f, "nextResponseHours");
  const errors: Record<string, string> = {};
  if (!text(f, "name")) errors.name = "Name the policy";
  if (!first || first < 1 || Number.isNaN(first)) errors.firstResponseHours = "Enter the first-response target in hours";
  if (!resolution || resolution < 1 || Number.isNaN(resolution)) errors.resolutionHours = "Enter the resolution target in hours";
  if (next !== null && (Number.isNaN(next) || next < 1)) errors.nextResponseHours = "Enter hours, or leave empty";
  if (Object.keys(errors).length) return { fieldErrors: errors, values: values(f) };
  return send(id ? `/sla-policies/${id}` : "/sla-policies", id ? "PUT" : "POST", {
    name: text(f, "name"), priority: optional(f, "priority"), category: optional(f, "category"), customerType: optional(f, "customerType"),
    firstResponseMinutes: first, nextResponseMinutes: next, resolutionMinutes: resolution,
    businessHours: on(f, "businessHours"), pauseWhileWaiting: on(f, "pauseWhileWaiting"), active: on(f, "active"),
  }, f, id ? "Policy saved. New and re-prioritised tickets use it." : "Policy added.");
}

export async function saveCalendarAction(_p: FormState, f: FormData): Promise<FormState> {
  const hours = [1, 2, 3, 4, 5, 6, 7].filter((d) => on(f, `day${d}`)).map((d) => ({
    dayOfWeek: d, opensAt: text(f, `opens${d}`), closesAt: text(f, `closes${d}`),
  }));
  if (hours.some((h) => !h.opensAt || !h.closesAt || h.opensAt >= h.closesAt)) {
    return { error: "Each working day needs an opening time before its closing time.", values: values(f) };
  }
  const holidays: { day: string; name: string }[] = [];
  for (const line of text(f, "holidays").split("\n").map((l) => l.trim()).filter(Boolean)) {
    const m = /^(\d{4}-\d{2}-\d{2})\s+(.{1,100})$/.exec(line);
    if (!m) return { fieldErrors: { holidays: `Write each holiday as “YYYY-MM-DD Name”: “${line.slice(0, 40)}”` }, values: values(f) };
    holidays.push({ day: m[1] ?? "", name: (m[2] ?? "").trim() });
  }
  return send("/calendar", "PUT", { hours, holidays }, f, "Business hours saved. SLA targets set from now on use them.");
}

export async function saveCategoryAction(id: string | null, parentId: string | null, category: string | null, _p: FormState,
  f: FormData): Promise<FormState> {
  if (!text(f, "name")) return { fieldErrors: { name: "Name it" }, values: values(f) };
  const code = text(f, "code").toUpperCase();
  if (!id && !/^[A-Z][A-Z0-9_]{1,39}$/.test(code)) return { fieldErrors: { code: "Capital letters, digits and _, e.g. CARD_PAYMENT" }, values: values(f) };
  return send(id ? `/categories/${id}` : "/categories", id ? "PUT" : "POST", {
    parentId, category, code: id ? null : code, name: text(f, "name"), description: optional(f, "description"),
    defaultPriority: optional(f, "defaultPriority"), defaultTeam: optional(f, "defaultTeam"),
    active: id ? on(f, "active") : true, sortOrder: int(f, "sortOrder"),
  }, f, id ? "Saved." : "Subcategory added.");
}

export async function saveAssignmentAction(category: string, _p: FormState, f: FormData): Promise<FormState> {
  const members = f.getAll("members").map(String).filter((m) => UUID.test(m));
  const strategy = text(f, "strategy");
  if (strategy !== "MANUAL" && members.length === 0) return { error: "Choose the agents to share these tickets between.", values: values(f) };
  return send(`/assignment/${category}`, "PUT", { strategy, members }, f, "Assignment rule saved.");
}

export async function saveLevelAction(level: number, _p: FormState, f: FormData): Promise<FormState> {
  if (!text(f, "name")) return { fieldErrors: { name: "Name the level" }, values: values(f) };
  return send(`/escalation-levels/${level}`, "PUT", {
    name: text(f, "name"), description: optional(f, "description"), contacts: f.getAll("contacts").map(String).filter((c) => UUID.test(c)),
  }, f, "Level saved.");
}

/** Builds a rule's structured conditions and parameters from the form; the API validates every key and value. */
export async function saveRuleAction(id: string | null, _p: FormState, f: FormData): Promise<FormState> {
  if (!text(f, "name")) return { fieldErrors: { name: "Name the rule" }, values: values(f) };
  const conditions: Record<string, unknown> = {};
  for (const key of ["priorities", "categories", "teams", "statuses", "channels", "metrics"]) {
    const list = f.getAll(key).map(String).filter(Boolean);
    if (list.length) conditions[key] = list;
  }
  const unassigned = text(f, "unassigned");
  if (unassigned) conditions.unassigned = unassigned === "true";
  for (const key of ["maxEscalationLevel", "minutes", "percent"]) {
    const v = int(f, key);
    if (v !== null) conditions[key] = v;
  }
  const parameters: Record<string, unknown> = {};
  if (text(f, "priority")) parameters.priority = text(f, "priority");
  if (int(f, "level") !== null) parameters.level = int(f, "level");
  if (text(f, "strategy")) parameters.strategy = text(f, "strategy");
  return send(id ? `/automation-rules/${id}` : "/automation-rules", id ? "PUT" : "POST", {
    name: text(f, "name"), trigger: text(f, "trigger"), action: text(f, "action"), conditions, parameters, enabled: on(f, "enabled"),
  }, f, id ? "Rule saved." : "Rule added.");
}
