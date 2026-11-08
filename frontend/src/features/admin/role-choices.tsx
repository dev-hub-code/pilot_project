"use client";

import { useMemo, useState } from "react";
import { Building2, Check, ShieldCheck, X } from "lucide-react";
import { DEPARTMENT_ICON, readableCode as readable, roleTitle as title } from "./role-labels";
import type { Role } from "@/types/auth";

/** A department, as far as the role picker needs it. */
export interface RoleDepartment {
  id: string;
  code: string;
  name: string;
}

const OTHER = "other";

/**
 * Roles grouped by department: pick a department, then tick its roles. Every role stays in the form (the other
 * departments' are only hidden), so roles can be chosen across departments; the chosen ones are listed on top.
 */
export function RoleChoices({ roles, departments = [], selected, error }: {
  roles: Role[];
  departments?: RoleDepartment[];
  selected: string[];
  error?: string;
}) {
  const [chosen, setChosen] = useState<Set<string>>(() => new Set(selected));
  const groups = useMemo(() => {
    const known = new Map(departments.map((d) => [d.id, d]));
    const byDept = new Map<string, Role[]>();
    for (const role of roles) {
      const key = role.departmentId && known.has(role.departmentId) ? role.departmentId : OTHER;
      byDept.set(key, [...(byDept.get(key) ?? []), role]);
    }
    const list = departments.filter((d) => byDept.has(d.id))
      .map((d) => ({ key: d.id, name: d.name, icon: DEPARTMENT_ICON[d.code] ?? Building2, roles: byDept.get(d.id)! }));
    if (byDept.has(OTHER)) {
      list.push({ key: OTHER, name: list.length ? "Other roles" : "Roles", icon: Building2, roles: byDept.get(OTHER)! });
    }
    return list;
  }, [roles, departments]);
  // Open the first department with a chosen role, else the first one.
  const [open, setOpen] = useState(() => groups.find((g) => g.roles.some((r) => selected.includes(r.name)))?.key ?? groups[0]?.key);
  const byName = new Map(roles.map((r) => [r.name, r]));

  const toggle = (name: string, on: boolean) => setChosen((prev) => {
    const next = new Set(prev);
    if (on) next.add(name); else next.delete(name);
    return next;
  });

  return (
    <fieldset className="space-y-4">
      <legend className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <ShieldCheck aria-hidden="true" className="size-4 text-gold-text" /> Roles
      </legend>
      <p className="-mt-2 text-xs text-muted">Pick a department, then tick the roles this person needs. You can choose roles in more than one department.</p>

      {/* What is chosen so far, across departments. */}
      <div aria-live="polite" className="flex min-h-11 flex-wrap items-center gap-2 rounded-xl border border-dashed border-border bg-background/60 p-2">
        {chosen.size === 0 ? (
          <span className="px-1 text-xs text-muted">No role chosen yet.</span>
        ) : [...chosen].map((name) => (
          <span key={name} className="inline-flex items-center gap-1 rounded-full bg-gold/10 py-1 pr-1 pl-2.5 text-xs font-medium text-gold-text ring-1 ring-gold/25">
            {byName.get(name) ? title(byName.get(name)!) : readable(name)}
            <button type="button" onClick={() => toggle(name, false)} aria-label={`Remove ${byName.get(name) ? title(byName.get(name)!) : name}`}
              className="flex size-5 items-center justify-center rounded-full transition-colors hover:bg-gold hover:text-on-gold">
              <X aria-hidden="true" className="size-3" />
            </button>
          </span>
        ))}
      </div>

      {groups.length > 1 && (
        <div role="tablist" aria-label="Departments" className="flex flex-wrap gap-2">
          {groups.map((g) => {
            const count = g.roles.filter((r) => chosen.has(r.name)).length;
            const active = g.key === open;
            return (
              <button key={g.key} type="button" role="tab" aria-selected={active} onClick={() => setOpen(g.key)}
                className={`inline-flex items-center gap-2 rounded-xl border px-3 py-2 text-sm font-medium transition-colors ${active
                  ? "border-ink bg-ink text-on-ink shadow-sm dark:border-gold dark:bg-gold dark:text-on-gold"
                  : "border-border bg-surface text-muted hover:border-gold/40 hover:text-foreground"}`}>
                <g.icon aria-hidden="true" className="size-4" />
                {g.name}
                <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${count > 0
                  ? active ? "bg-on-ink/20 dark:bg-on-gold/20" : "bg-gold text-on-gold"
                  : active ? "bg-on-ink/10 dark:bg-on-gold/10" : "bg-background ring-1 ring-border"}`}>
                  {count > 0 ? `${count}/${g.roles.length}` : g.roles.length}
                </span>
              </button>
            );
          })}
        </div>
      )}

      {groups.map((g) => (
        <div key={g.key} role={groups.length > 1 ? "tabpanel" : undefined} aria-label={g.name} hidden={g.key !== open}
          className="grid gap-2 sm:grid-cols-2">
          {g.roles.map((r) => {
            const on = chosen.has(r.name);
            return (
              <label key={r.id}
                className={`flex cursor-pointer items-start gap-3 rounded-xl border px-3 py-3 text-sm transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-gold/40 ${on
                  ? "border-gold bg-gold/5" : "border-border bg-surface hover:border-gold/50"}`}>
                <input type="checkbox" name="roles" value={r.name} checked={on} onChange={(e) => toggle(r.name, e.target.checked)} className="sr-only" />
                <span aria-hidden="true" className={`mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-md transition-colors ${on
                  ? "bg-gold text-on-gold" : "ring-1 ring-border"}`}>
                  {on && <Check className="size-3.5" />}
                </span>
                <span className="min-w-0">
                  <span className="block font-medium">{title(r)}</span>
                  <span className="block text-xs text-muted">
                    {r.permissions.length} {r.permissions.length === 1 ? "permission" : "permissions"}
                    {r.employees > 0 ? ` · ${r.employees} ${r.employees === 1 ? "person has it" : "people have it"}` : ""}
                  </span>
                </span>
              </label>
            );
          })}
        </div>
      ))}
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </fieldset>
  );
}
