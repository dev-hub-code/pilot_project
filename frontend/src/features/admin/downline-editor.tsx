"use client";

import { useActionState, useEffect, useId, useMemo, useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmailText } from "@/components/ui/email-text";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { SelectField } from "@/components/ui/select-field";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { TextArea } from "@/components/ui/text-area";
import type { AdminDownline, AdminDownlineMember } from "@/types/referral";
import { formatDate, humanize } from "@/utils/format";
import type { FormState } from "@/validators/form-state";
import { NO_REFERRER } from "@/validators/referral";
import { moveReferralAction } from "./referral-actions";

/** Levels 1-4 earn commission; deeper members are shown so they can be arranged too. */
const COMMISSION_LEVELS = 4;
/** Drop target id of the "Remove from downline" area. */
const REMOVE = "remove";

/** A person in a pane: the investor at its top (level 0, joinedAt null) or a member. */
interface Node {
  userId: string;
  referrerId: string | null;
  level: number;
  name: string;
  email: string;
  status: string;
  joinedAt: string | null;
  children: Node[];
}

/** A move being confirmed: the person, and the referrer it was dropped on ("" when opened with "Move"). */
interface Pending {
  member: Node;
  referrerId: string;
}

type DropProps = (targetId: string) => React.HTMLAttributes<HTMLElement>;

/**
 * Investors' downlines, side by side, for a super administrator to rearrange: drag anyone (everyone below them comes
 * along) onto a new referrer in either downline, or onto "Remove from downline" so nobody refers them, then confirm
 * with a reason. Nobody can be dropped onto themselves, anyone below them, or the referrer they already have. "Move"
 * opens the same confirmation without dragging (keyboards, touch screens).
 *
 * @param side shown beside the first downline when there is only one (choosing a second), else above the second
 */
export function DownlineEditor({ panes, side }: { panes: AdminDownline[]; side: React.ReactNode }) {
  const roots = useMemo(() => panes.map(buildTree), [panes]);
  const [dragged, setDragged] = useState<Node | null>(null);
  const [over, setOver] = useState<string | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);
  const [choice, setChoice] = useState("");
  const [state, formAction, saving] = useActionState<FormState, FormData>(moveReferralAction, {});
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  // The ids someone may not move under: themselves and everyone below them, in whichever downline shows them.
  const blocked = useMemo(() => (dragged ? new Set(roots.flatMap((r) => below(r, dragged.userId))) : null), [dragged, roots]);
  const canDropOn = (targetId: string) => {
    if (!dragged) return false;
    if (targetId === REMOVE) return dragged.referrerId !== null;
    return !blocked!.has(targetId) && dragged.referrerId !== targetId;
  };

  function open(member: Node, referrerId: string) {
    setPending({ member, referrerId });
    setChoice(referrerId);
  }

  // The confirmation opens once a move is pending (it has rendered by then).
  useEffect(() => {
    if (pending && !dialog.current?.open) dialog.current?.showModal();
  }, [pending]);

  // A successful move re-renders the page with the new downlines: close the confirmation.
  const handled = useRef(state);
  useEffect(() => {
    if (state === handled.current) return;
    handled.current = state;
    if (state.success) dialog.current?.close();
  }, [state]);

  const dropProps: DropProps = (targetId) => ({
    onDragOver: (event) => {
      if (!canDropOn(targetId)) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      if (over !== targetId) setOver(targetId);
    },
    onDragLeave: (event) => {
      if (!event.currentTarget.contains(event.relatedTarget as globalThis.Node | null)) setOver(null);
    },
    onDrop: (event) => {
      event.preventDefault();
      setOver(null);
      if (dragged && canDropOn(targetId)) open(dragged, targetId === REMOVE ? NO_REFERRER : targetId);
      setDragged(null);
    },
  });

  const drag = {
    dragged, over, canDropOn, dropProps,
    onDragStart: setDragged,
    onDragEnd: () => { setDragged(null); setOver(null); },
    onMove: (node: Node) => open(node, ""),
  };
  const targets = pending ? referrerOptions(roots, pending.member) : [];
  const brought = pending ? roots.map((r) => below(r, pending.member.userId).length).reduce((a, b) => Math.max(a, b), 0) - 1 : 0;
  const removing = choice === NO_REFERRER;
  const removable = dragged !== null && dragged.referrerId !== null;
  const [first, second] = roots;
  if (!first) return null;

  return (
    <div className="space-y-4">
      {state.success && <Notice tone="success">{state.success}</Notice>}

      <div {...dropProps(REMOVE)} aria-hidden={!dragged}
        className={`sticky top-2 z-10 border-2 border-dashed px-4 py-3 text-center text-sm transition-colors ${
          over === REMOVE ? "border-rose-600 bg-rose-50 text-rose-800 dark:bg-rose-950 dark:text-rose-200"
            : removable ? "border-rose-400 bg-surface text-rose-700 dark:text-rose-300" : "border-border bg-surface text-muted"}`}>
        {removable ? `Drop here to remove ${dragged.name} from their downline`
          : dragged ? `Nobody refers ${dragged.name}: drop them onto someone to place them in a downline`
            : "Remove from downline: drag someone here so nobody refers them (their own downline stays with them)"}
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-2">
        <Pane root={first} {...drag} />
        <div className="space-y-3">
          {side}
          {second && <Pane root={second} {...drag} />}
        </div>
      </div>

      <dialog ref={dialog} aria-labelledby={titleId}
        onClose={() => setPending(null)}
        className="m-auto max-h-[calc(100dvh-2rem)] w-[min(32rem,calc(100vw-2rem))] overflow-y-auto border border-border bg-surface p-0 text-foreground shadow-2xl backdrop:bg-ink/60 backdrop:backdrop-blur-[2px] open:animate-[confirm-in_150ms_ease-out]">
        {pending && (
          <form action={formAction} className="space-y-5 p-6" noValidate>
            <div className="space-y-1.5">
              <h2 id={titleId} className="font-semibold">
                {removing ? `Remove ${pending.member.name} from their downline` : `Move ${pending.member.name}`}
              </h2>
              <p className="text-sm leading-relaxed text-muted">
                {pending.member.name}
                {brought > 0 ? ` and the ${brought} ${brought === 1 ? "member" : "members"} below them` : ""}
                {removing ? " will no longer be in anyone's downline: nobody refers them." : " will be referred by the person you choose."}
                {" "}Commission from now on follows the new structure; commission already paid stays where it is.
              </p>
            </div>
            {state.error && <Alert>{state.error}</Alert>}
            <input type="hidden" name="memberId" value={pending.member.userId} />
            <SelectField label="New referrer" name="referrerId" options={targets} placeholder="Choose…"
              value={choice} onChange={(event) => setChoice(event.target.value)} error={state.fieldErrors?.referrerId} />
            <TextArea label="Reason" name="reason" rows={3} required maxLength={500}
              placeholder="Why the referrer changes (kept in the audit log)" error={state.fieldErrors?.reason} />
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="quiet" onClick={() => dialog.current?.close()}>Cancel</Button>
              <Button type="submit" variant={removing ? "danger" : "primary"} disabled={saving}>
                {saving ? "Saving…" : removing ? "Remove" : "Move"}
              </Button>
            </div>
          </form>
        )}
      </dialog>
    </div>
  );
}

interface DragProps {
  dragged: Node | null;
  over: string | null;
  canDropOn: (id: string) => boolean;
  dropProps: DropProps;
  onDragStart: (node: Node) => void;
  onDragEnd: () => void;
  onMove: (node: Node) => void;
}

/** One investor's downline: the investor on top (draggable too), their members below. */
function Pane({ root, ...props }: { root: Node } & DragProps) {
  const { dropProps } = props;
  const referrer = root.referrerId ? "Referred by someone above them" : "Nobody referred them";
  return (
    <section className="space-y-2 lg:max-h-[75vh] lg:overflow-y-auto lg:pr-1">
      <div {...dropProps(root.userId)} {...dragHandlers(root, props)}
        className={`flex cursor-grab flex-wrap items-center justify-between gap-3 border-2 p-4 transition-colors active:cursor-grabbing ${
          targetStyle(root, props, "border-foreground/30")}`}>
        <div className="min-w-0">
          <p className="text-xs font-medium tracking-[0.08em] text-muted uppercase">Investor · {referrer}</p>
          <a href={`/admin/users/${root.userId}`} className="font-semibold hover:underline" draggable={false}>{root.name}</a>
          {root.status !== "ACTIVE" && <> <StatusBadge tone={toneFor(root.status)}>{humanize(root.status)}</StatusBadge></>}
          <p className="text-sm text-muted wrap-anywhere"><EmailText>{root.email}</EmailText></p>
        </div>
        <Button type="button" variant="quiet" className="min-h-9 px-3 text-xs" onClick={() => props.onMove(root)}>Move</Button>
      </div>
      {root.children.length === 0 ? (
        <EmptyState title="No downline yet" description={`Nobody is in ${root.name}'s downline.`} />
      ) : (
        <Branch nodes={root.children} {...props} />
      )}
    </section>
  );
}

function Branch({ nodes, ...props }: { nodes: Node[] } & DragProps) {
  return (
    <ul className="space-y-2">
      {nodes.map((node) => <Member key={node.userId} node={node} {...props} />)}
    </ul>
  );
}

function Member({ node, ...props }: { node: Node } & DragProps) {
  const { dropProps, onMove } = props;
  return (
    <li className="space-y-2">
      <div {...dropProps(node.userId)} {...dragHandlers(node, props)}
        className={`flex cursor-grab flex-wrap items-center justify-between gap-x-4 gap-y-2 border-2 px-4 py-3 text-sm transition-colors active:cursor-grabbing ${
          targetStyle(node, props, "border-border")}`}>
        <span className="flex min-w-0 items-center gap-3">
          <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4 shrink-0 text-muted" fill="currentColor">
            <circle cx="9" cy="6" r="1.5" /><circle cx="15" cy="6" r="1.5" /><circle cx="9" cy="12" r="1.5" />
            <circle cx="15" cy="12" r="1.5" /><circle cx="9" cy="18" r="1.5" /><circle cx="15" cy="18" r="1.5" />
          </svg>
          <span className="min-w-0">
            <span className="flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border px-2 py-0.5 text-[11px] tracking-[0.08em] text-muted uppercase">
                Level {node.level}{node.level > COMMISSION_LEVELS ? " · no commission" : ""}
              </span>
              <a href={`/admin/users/${node.userId}`} className="font-medium hover:underline" draggable={false}>{node.name}</a>
              {node.status !== "ACTIVE" && <StatusBadge tone={toneFor(node.status)}>{humanize(node.status)}</StatusBadge>}
            </span>
            <span className="block text-xs text-muted wrap-anywhere">
              <EmailText>{node.email}</EmailText>{node.joinedAt && <> · joined {formatDate(node.joinedAt)}</>}
            </span>
          </span>
        </span>
        <span className="flex items-center gap-3">
          <Button type="button" variant="quiet" className="min-h-9 px-3 text-xs" onClick={() => onMove(node)}>Move</Button>
        </span>
      </div>
      {node.children.length > 0 && (
        <div className="ml-4 border-l border-border pl-4">
          <Branch nodes={node.children} {...props} />
        </div>
      )}
    </li>
  );
}

/**
 * While dragging, where the person may go shows dashed gold and everyone else fades; the one under the pointer is
 * filled. Only colours change, so nothing shifts under the pointer.
 */
function targetStyle(node: Node, { dragged, over, canDropOn }: DragProps, border: string) {
  if (over === node.userId) return "border-gold bg-gold/10";
  if (!dragged) return `${border} bg-surface`;
  if (dragged.userId === node.userId) return `${border} bg-surface opacity-40`;
  return canDropOn(node.userId) ? "border-dashed border-gold/70 bg-surface" : `${border} bg-surface opacity-50`;
}

function dragHandlers(node: Node, { onDragStart, onDragEnd }: DragProps): React.HTMLAttributes<HTMLElement> {
  return {
    draggable: true,
    onDragStart: (event) => {
      event.stopPropagation();
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", node.userId);
      onDragStart(node);
    },
    onDragEnd,
  };
}

/** The downline as a tree under its investor; members whose referrer is not listed (a truncated list) hang off the investor. */
function buildTree({ investor, members }: AdminDownline): Node {
  const root: Node = { ...investor, level: 0, joinedAt: null, children: [] };
  const byId = new Map<string, Node>(members.map((m: AdminDownlineMember) => [m.userId, { ...m, children: [] }]));
  for (const node of byId.values()) {
    (byId.get(node.referrerId!) ?? root).children.push(node);
  }
  return root;
}

/** The person with this id and everyone below them in this tree; empty when they are not in it. */
function below(node: Node, userId: string): string[] {
  if (node.userId === userId) return all(node);
  return node.children.flatMap((child) => below(child, userId));
}

function all(node: Node): string[] {
  return [node.userId, ...node.children.flatMap(all)];
}

/**
 * Who the person may move under: anyone shown outside their own branch, except their current referrer, or nobody
 * (taking them out of their downline) when someone refers them now.
 */
function referrerOptions(roots: Node[], member: Node) {
  const excluded = new Set(roots.flatMap((r) => below(r, member.userId)));
  if (member.referrerId) excluded.add(member.referrerId);
  const options = member.referrerId ? [{ value: NO_REFERRER, label: "Nobody: remove from the downline" }] : [];
  const seen = new Set<string>();
  for (const root of roots) {
    const add = (node: Node) => {
      if (!excluded.has(node.userId) && !seen.has(node.userId)) {
        seen.add(node.userId);
        options.push({
          value: node.userId,
          label: node.level === 0 ? `${node.name} (investor)` : `${node.name} · level ${node.level} in ${root.name}'s downline`,
        });
      }
      node.children.forEach(add);
    };
    add(root);
  }
  return options;
}
