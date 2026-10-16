"use client";

import { useState, useTransition, type DragEvent, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import type { DashboardLayout } from "@/types/report";
import type { FormState } from "@/validators/form-state";

/** One card of the overview, rendered on the server; the board only arranges it. */
export interface Widget {
  key: string;
  /** How the card is named in the "add cards" tray. */
  title: string;
  /** Columns it takes on a wide screen: a figure 1, a chart 2, a table the full row. */
  span: 1 | 2 | 4;
  node: ReactNode;
}

interface Arrangement {
  cards: string[];
  hidden: string[];
}

const SPAN: Record<Widget["span"], string> = { 1: "", 2: "sm:col-span-2", 4: "sm:col-span-2 lg:col-span-4" };

/**
 * The saved arrangement, kept to the cards the viewer can see now. Cards it does not mention (new to the viewer since
 * they saved) are shown at the end; without a saved arrangement, every card shows in the default order.
 */
function arrange(widgets: Widget[], layout: DashboardLayout): Arrangement {
  const keys = widgets.map((w) => w.key);
  if (!layout.updatedAt) return { cards: keys, hidden: [] };
  const available = new Set(keys);
  const cards = layout.cards.filter((k) => available.has(k));
  const hidden = layout.hidden.filter((k) => available.has(k));
  const placed = new Set([...cards, ...hidden]);
  return { cards: [...cards, ...keys.filter((k) => !placed.has(k))], hidden };
}

/** Puts `key` on the board before `before` (at the end without one), wherever it was. */
function place(a: Arrangement, key: string, before: string | null): Arrangement {
  const cards = a.cards.filter((k) => k !== key);
  const at = before === null ? -1 : cards.indexOf(before);
  cards.splice(at === -1 ? cards.length : at, 0, key);
  return { cards, hidden: a.hidden.filter((k) => k !== key) };
}

function remove(a: Arrangement, key: string): Arrangement {
  return { cards: a.cards.filter((k) => k !== key), hidden: [...a.hidden.filter((k) => k !== key), key] };
}

/**
 * The overview's cards in the viewer's own order, among those their roles allow. "Add card" puts a removed card back,
 * saved at once. "Customize" lets them drag cards to reorder them, drag a card to the tray (or press ×) to remove it,
 * and drag one from the tray onto the board (or press +) to add it back; the arrows move a card without dragging, for
 * keyboards and touch screens.
 */
export function DashboardBoard({ widgets, layout, saveAction, resetAction }: {
  widgets: Widget[];
  layout: DashboardLayout;
  saveAction: (cards: string[], hidden: string[]) => Promise<FormState>;
  resetAction: () => Promise<FormState>;
}) {
  const byKey = new Map(widgets.map((w) => [w.key, w]));
  const [saved, setSaved] = useState<Arrangement>(() => arrange(widgets, layout));
  const [draft, setDraft] = useState<Arrangement>(saved);
  const [editing, setEditing] = useState(false);
  const [dragging, setDragging] = useState<string | null>(null);
  const [over, setOver] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<FormState>({});
  const [adding, setAdding] = useState(false);
  const [pending, startTransition] = useTransition();
  const shown = editing ? draft : saved;

  const startDrag = (key: string) => (event: DragEvent) => {
    event.dataTransfer.setData("text/plain", key);
    event.dataTransfer.effectAllowed = "move";
    setDragging(key);
  };
  const endDrag = () => {
    setDragging(null);
    setOver(null);
  };
  const allowDrop = (target: string | null) => (event: DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    setOver(target);
  };
  const dropOnBoard = (before: string | null) => (event: DragEvent) => {
    event.preventDefault();
    event.stopPropagation();
    const key = event.dataTransfer.getData("text/plain") || dragging;
    if (key && byKey.has(key) && key !== before) setDraft((a) => place(a, key, before));
    endDrag();
  };
  const dropOnTray = (event: DragEvent) => {
    event.preventDefault();
    const key = event.dataTransfer.getData("text/plain") || dragging;
    if (key && byKey.has(key)) setDraft((a) => remove(a, key));
    endDrag();
  };
  const shift = (key: string, by: -1 | 1) => setDraft((a) => {
    const i = a.cards.indexOf(key);
    const j = i + by;
    if (i === -1 || j < 0 || j >= a.cards.length) return a;
    const cards = a.cards.filter((k) => k !== key);
    cards.splice(j, 0, key);
    return { ...a, cards };
  });

  const begin = () => {
    setDraft(saved);
    setFeedback({});
    setAdding(false);
    setEditing(true);
  };
  /** Adds a removed card back outside Customize, saved at once. */
  const persist = (next: Arrangement, success: string) => startTransition(async () => {
    const result = await saveAction(next.cards, next.hidden);
    setFeedback(result.error ? result : { success });
    if (!result.error) {
      setSaved(next);
      if (next.hidden.length === 0) setAdding(false);
    }
  });
  const save = () => startTransition(async () => {
    const result = await saveAction(draft.cards, draft.hidden);
    setFeedback(result);
    if (!result.error) {
      setSaved(draft);
      setEditing(false);
    }
  });
  const reset = () => startTransition(async () => {
    const result = await resetAction();
    setFeedback(result);
    if (!result.error) {
      const defaults = { cards: widgets.map((w) => w.key), hidden: [] };
      setSaved(defaults);
      setDraft(defaults);
      setEditing(false);
    }
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted">
          {editing
            ? "Drag cards to move them. Drag a card to the tray below (or press ×) to remove it, and from the tray onto the board (or press +) to add it."
            : null}
        </p>
        <div className="flex flex-wrap gap-2">
          {editing ? (
            <>
              <Button variant="quiet" onClick={() => setEditing(false)} disabled={pending}>Cancel</Button>
              <Button variant="quiet" onClick={reset} disabled={pending}>Reset to default</Button>
              <Button onClick={save} disabled={pending}>{pending ? "Saving…" : "Save dashboard"}</Button>
            </>
          ) : (
            <>
              {saved.hidden.length > 0 && (
                <Button variant="quiet" onClick={() => setAdding((open) => !open)} aria-expanded={adding} disabled={pending}>
                  + Add card ({saved.hidden.length})
                </Button>
              )}
              <Button variant="secondary" onClick={begin}>Customize</Button>
            </>
          )}
        </div>
      </div>
      <FormFeedback state={feedback} />

      {adding && !editing && saved.hidden.length > 0 && (
        <section aria-label="Cards you removed" className="space-y-3 border border-border bg-surface p-4">
          <h2 className="text-sm font-medium">Cards you removed</h2>
          <ul className="flex flex-wrap gap-2">
            {saved.hidden.map((key) => {
              const widget = byKey.get(key);
              if (!widget) return null;
              return (
                <li key={key} className="flex items-center gap-2 border border-border py-1 pl-3 pr-1 text-sm">
                  {widget.title}
                  <CardControl label={`Add ${widget.title}`} disabled={pending}
                    onClick={() => persist(place(saved, key, null), `${widget.title} added.`)}>+</CardControl>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {shown.cards.length === 0 && !editing ? (
        <p className="border border-dashed border-border p-8 text-center text-sm text-muted">
          No cards on your dashboard. Press Customize to add some.
        </p>
      ) : (
        <div className={`grid min-h-24 gap-4 sm:grid-cols-2 lg:grid-cols-4 ${editing ? "border border-dashed border-border p-3" : ""}`}
          onDragOver={editing ? allowDrop(null) : undefined} onDrop={editing ? dropOnBoard(null) : undefined}
          aria-label="Dashboard cards">
          {shown.cards.map((key, index) => {
            const widget = byKey.get(key);
            if (!widget) return null;
            if (!editing) return <div key={key} className={`min-w-0 ${SPAN[widget.span]}`}>{widget.node}</div>;
            return (
              <div key={key} draggable onDragStart={startDrag(key)} onDragEnd={endDrag}
                onDragOver={allowDrop(key)} onDrop={dropOnBoard(key)}
                className={`relative min-w-0 cursor-grab ${SPAN[widget.span]} ${dragging === key ? "opacity-40" : ""} ${
                  over === key && dragging !== key ? "outline-2 outline-offset-2 outline-gold" : ""}`}>
                <div className="pointer-events-none select-none" aria-hidden>{widget.node}</div>
                <div className="absolute right-2 top-2 flex gap-1">
                  <CardControl label={`Move ${widget.title} earlier`} onClick={() => shift(key, -1)} disabled={index === 0}>←</CardControl>
                  <CardControl label={`Move ${widget.title} later`} onClick={() => shift(key, 1)}
                    disabled={index === shown.cards.length - 1}>→</CardControl>
                  <CardControl label={`Remove ${widget.title}`} onClick={() => setDraft((a) => remove(a, key))}>×</CardControl>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editing && (
        <section aria-label="Add cards" onDragOver={allowDrop("tray")} onDrop={dropOnTray}
          className={`space-y-3 border border-dashed p-4 ${over === "tray" ? "border-gold bg-background" : "border-border"}`}>
          <h2 className="text-sm font-medium">Add cards</h2>
          {draft.hidden.length === 0 ? (
            <p className="text-sm text-muted">Every card is on your dashboard. Drag one here to remove it.</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {draft.hidden.map((key) => {
                const widget = byKey.get(key);
                if (!widget) return null;
                return (
                  <li key={key} draggable onDragStart={startDrag(key)} onDragEnd={endDrag}
                    className="flex cursor-grab items-center gap-2 border border-border bg-surface py-1 pl-3 pr-1 text-sm">
                    {widget.title}
                    <CardControl label={`Add ${widget.title}`} onClick={() => setDraft((a) => place(a, key, null))}>+</CardControl>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}

function CardControl({ label, onClick, disabled, children }: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick} disabled={disabled}
      className="flex size-8 items-center justify-center border border-border bg-surface text-sm hover:border-foreground/40 disabled:opacity-40">
      {children}
    </button>
  );
}
