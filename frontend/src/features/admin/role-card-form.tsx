"use client";

import { useActionState, useState } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { DashboardCardInfo } from "@/types/report";
import type { FormState } from "@/validators/form-state";
import { chooseRoleCardsAction } from "./role-card-actions";

/** Ticks the dashboard cards a role shows, grouped as on the dashboard. */
export function RoleCardForm({ roleId, catalog, chosen, editable }: {
  roleId: string;
  catalog: DashboardCardInfo[];
  chosen: string[];
  editable: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(chooseRoleCardsAction.bind(null, roleId), {});
  const [ticked, setTicked] = useState(() => new Set(chosen));
  const groups = [...new Set(catalog.map((c) => c.group))];
  const toggle = (keys: string[], on: boolean) => setTicked((current) => {
    const next = new Set(current);
    keys.forEach((k) => (on ? next.add(k) : next.delete(k)));
    return next;
  });

  return (
    <form action={action} className="space-y-5">
      <FormFeedback state={state} />
      {editable && (
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="quiet" onClick={() => toggle(catalog.map((c) => c.key), true)}>Tick all</Button>
          <Button type="button" variant="quiet" onClick={() => toggle(catalog.map((c) => c.key), false)}>Untick all</Button>
        </div>
      )}
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {groups.map((group) => {
          const cards = catalog.filter((c) => c.group === group);
          return (
            <fieldset key={group} className="space-y-2 border border-border p-4" disabled={!editable}>
              <legend className="px-1 text-sm font-medium">{group}</legend>
              {editable && (
                <button type="button" className="text-xs text-gold-text hover:underline"
                  onClick={() => toggle(cards.map((c) => c.key), !cards.every((c) => ticked.has(c.key)))}>
                  {cards.every((c) => ticked.has(c.key)) ? "Untick group" : "Tick group"}
                </button>
              )}
              {cards.map((card) => (
                <label key={card.key} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" name="cards" value={card.key} checked={ticked.has(card.key)}
                    onChange={(e) => toggle([card.key], e.target.checked)} className="size-4 accent-gold" />
                  {card.title}
                </label>
              ))}
            </fieldset>
          );
        })}
      </div>
      {editable && (
        <SubmitButton confirm={ticked.size === 0 ? "Show no cards to this role? Their dashboard will be empty." : undefined}>
          Save cards ({ticked.size} of {catalog.length})
        </SubmitButton>
      )}
    </form>
  );
}
