"use client";

import { useMemo, useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
import { saveRoleAction } from "@/features/admin/portal-actions";
import type { DepartmentSummary, PermissionEntry } from "@/types/admin-portal";
import type { Role } from "@/types/auth";
import { humanize } from "@/utils/format";
import type { FormState } from "@/validators/form-state";

/**
 * Chooses a role's permissions. The confirmation before widening a role is a courtesy: the API refuses any grant
 * the signed-in employee does not hold themselves.
 */
export function RoleEditor({ role, catalog, departments, readOnly }: {
  role?: Role;
  catalog: readonly PermissionEntry[];
  departments: readonly DepartmentSummary[];
  readOnly: boolean;
}) {
  const original = useMemo(() => new Set(role?.permissions ?? []), [role]);
  const [chosen, setChosen] = useState<ReadonlySet<string>>(original);
  const [search, setSearch] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState(role?.description ?? "");
  const [departmentId, setDepartmentId] = useState(role?.departmentId ?? "");
  const [state, setState] = useState<FormState>({});
  const [pending, start] = useTransition();
  const { ask, dialog } = useConfirm();

  const added = [...chosen].filter((c) => !original.has(c)).sort();
  const removed = [...original].filter((c) => !chosen.has(c)).sort();
  const wanted = search.trim().toLowerCase();
  const shown = catalog.filter((p) => !wanted
    || p.code.toLowerCase().includes(wanted) || p.description.toLowerCase().includes(wanted));
  const categories = [...new Set(shown.map((p) => p.category))];
  const sensitive = new Set(catalog.filter((p) => p.sensitive).map((p) => p.code));

  function change(codes: readonly string[], on: boolean) {
    setChosen((was) => {
      const next = new Set(was);
      codes.forEach((code) => (on ? next.add(code) : next.delete(code)));
      return next;
    });
  }

  async function save() {
    if (added.some((c) => sensitive.has(c))) {
      const go = await ask(`This adds sensitive permissions: ${added.filter((c) => sensitive.has(c)).join(", ")}. Continue?`,
        { confirmLabel: "Save role", danger: true });
      if (!go) return;
    }
    start(async () => {
      setState(await saveRoleAction(role?.id ?? null, role?.version ?? 0, {
        name, description, permissions: [...chosen].sort(), departmentId: departmentId || null,
      }));
    });
  }

  return (
    <div className="space-y-6">
      {dialog}
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-3">
        {!role && <TextField label="Name" name="name" value={name} placeholder="e.g. TREASURY_ANALYST"
          onChange={(e) => setName(e.target.value.toUpperCase())} />}
        <TextField label="Description" name="description" value={description} readOnly={readOnly}
          onChange={(e) => setDescription(e.target.value)} />
        <SelectField label="Department" name="departmentId" value={departmentId} disabled={readOnly} placeholder="None"
          options={departments.map((d) => ({ value: d.id, label: d.name }))}
          onChange={(e) => setDepartmentId(e.target.value)} />
      </div>

      <TextField label="Search permissions" name="search" value={search} onChange={(e) => setSearch(e.target.value)} />

      {categories.map((category) => {
        const codes = shown.filter((p) => p.category === category);
        const usable = codes.filter((p) => p.code !== "INVESTOR_PORTAL");
        return (
          <fieldset key={category} className="border border-border bg-surface p-4" disabled={readOnly}>
            <legend className="px-1 text-sm font-semibold">{humanize(category)}</legend>
            {!readOnly && (
              <div className="mb-3 flex gap-4 text-xs">
                <button type="button" className="text-gold-text hover:underline"
                  onClick={() => change(usable.map((p) => p.code), true)}>Select all</button>
                <button type="button" className="text-gold-text hover:underline"
                  onClick={() => change(usable.map((p) => p.code), false)}>Clear</button>
              </div>
            )}
            <ul className="grid gap-2 md:grid-cols-2">
              {usable.map((p) => (
                <li key={p.code}>
                  <label className="flex items-start gap-2 text-sm">
                    <input type="checkbox" className="mt-1" checked={chosen.has(p.code)}
                      onChange={(e) => change([p.code], e.target.checked)} />
                    <span>
                      <span className="font-mono text-xs">{p.code}</span>
                      {p.sensitive && <> <StatusBadge tone="warning">Sensitive</StatusBadge></>}
                      {p.reserved && <> <StatusBadge tone="neutral">Reserved</StatusBadge></>}
                      <span className="block text-xs text-muted">{p.description}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </fieldset>
        );
      })}

      {!readOnly && (
        <div className="sticky bottom-0 space-y-3 border border-border bg-surface p-4">
          <p className="text-sm">
            <span className="font-medium">{chosen.size}</span> permission(s).
            {added.length > 0 && <span className="text-emerald-700 dark:text-emerald-400"> Adding: {added.join(", ")}.</span>}
            {removed.length > 0 && <span className="text-rose-600 dark:text-rose-400"> Removing: {removed.join(", ")}.</span>}
            {role && role.employees > 0 && (added.length > 0 || removed.length > 0)
              && <span className="text-muted"> {role.employees} employee(s) will be signed out.</span>}
          </p>
          <div className="flex gap-3">
            <Button type="button" disabled={pending} onClick={() => void save()}>{pending ? "Saving…" : "Save role"}</Button>
            <Button type="button" variant="secondary" disabled={pending}
              onClick={() => { setChosen(original); setDescription(role?.description ?? ""); setState({}); }}>Cancel</Button>
          </div>
        </div>
      )}
    </div>
  );
}
