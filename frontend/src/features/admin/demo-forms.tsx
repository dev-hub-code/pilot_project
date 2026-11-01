"use client";

import { useActionState, useState } from "react";
import { currencyLabel } from "@/utils/money";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { yearsLabel } from "@/features/orders/lease-years";
import type { DemoMember, DemoPlan } from "@/types/demo";
import { formatDate } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";
import type { FormState } from "@/validators/form-state";
import {
  addDemoMemberAction, addDemoOrderAction, changeDemoFrameAction, createDemoAccountAction, removeDemoAccountAction,
  removeDemoMemberAction, removeDemoOrderAction,
} from "./demo-actions";

function useForm(action: (state: FormState, formData: FormData) => Promise<FormState>) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback?: string) => state.values?.[name] ?? fallback;
  return { state, formAction, e, v };
}

/** Marks the chosen investor as a demo account over a time frame. */
export function CreateDemoAccountForm({ userId, name, defaultStart, defaultEnd }: {
  userId: string;
  name: string;
  defaultStart: string;
  defaultEnd: string;
}) {
  const { state, formAction, e, v } = useForm(createDemoAccountAction);
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <input type="hidden" name="userId" value={userId} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Time frame starts" name="startsOn" type="date" defaultValue={v("startsOn", defaultStart)} error={e.startsOn} />
        <TextField label="Time frame ends" name="endsOn" type="date" defaultValue={v("endsOn", defaultEnd)} error={e.endsOn} />
      </div>
      <SubmitButton confirm={`Make ${name} a demo account? Their own pages will show dummy figures, and real withdrawal runs will leave them out.`}>
        Make demo account
      </SubmitButton>
    </form>
  );
}

export function DemoFrameForm({ userId, startsOn, endsOn }: { userId: string; startsOn: string; endsOn: string }) {
  const { state, formAction, e, v } = useForm(changeDemoFrameAction.bind(null, userId));
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Starts" name="startsOn" type="date" defaultValue={v("startsOn", startsOn)} error={e.startsOn} />
        <TextField label="Ends" name="endsOn" type="date" defaultValue={v("endsOn", endsOn)} error={e.endsOn} />
      </div>
      <SubmitButton variant="secondary">Change time frame</SubmitButton>
    </form>
  );
}

/** A dummy order of a real plan: the investor's own, or a downline member's (which earns the investor commission). */
export function DemoOrderForm({ userId, members, plans, startsOn }: {
  userId: string;
  members: DemoMember[];
  plans: DemoPlan[];
  startsOn: string;
}) {
  const { state, formAction, e, v } = useForm(addDemoOrderAction.bind(null, userId));
  const [planId, setPlanId] = useState(v("productId", plans[0]?.id ?? "") ?? "");
  const [amount, setAmount] = useState(v("amount", "") ?? "");
  const [memberId, setMemberId] = useState(v("memberId", "") ?? "");
  const [start, setStart] = useState(v("startsOn", startsOn) ?? startsOn);
  const member = members.find((m) => m.id === memberId);
  // A member's lease starts on or after they joined: picking them moves an earlier start to that day.
  const chooseMember = (id: string) => {
    setMemberId(id);
    const joined = members.find((m) => m.id === id)?.joinedOn;
    if (joined && start < joined) setStart(joined);
  };
  const plan = plans.find((p) => p.id === planId);
  const entered = Number(amount);
  const total = plan && Number.isFinite(entered) && entered > 0 ? entered : null;
  if (plans.length === 0) {
    return <p className="text-sm text-muted">No open or closed plan yet. Publish a plan under Investment plans first.</p>;
  }
  const owners = [{ value: "", label: "The investor (their own order)" },
    ...members.map((m) => ({ value: m.id, label: `${m.name} · level ${m.level}` }))];
  const planOptions = plans.map((p) => ({
    value: p.id,
    label: `${p.code} · ${p.title}${p.status === "CLOSED" ? " (closed)" : ""}`,
  }));
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Whose order" name="memberId" options={owners} value={memberId}
          onChange={(event) => chooseMember(event.target.value)} error={e.memberId} />
        <SelectField label="Plan" name="productId" options={planOptions} value={planId}
          onChange={(event) => setPlanId(event.target.value)} error={e.productId} />
      </div>
      {plan && (
        <p className="text-xs text-muted">
          Runs for {yearsLabel(plan.tenureYears)} at {formatPercent(plan.annualReturnPercent, 2)} a year.
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label={currencyLabel("Amount")} name="amount" inputMode="decimal" value={amount}
          onChange={(event) => setAmount(event.target.value)} error={e.amount} />
        <TextField label={member ? `Starts (${member.name} joined ${formatDate(member.joinedOn)})` : "Starts"}
          name="startsOn" type="date" value={start} min={member?.joinedOn} onChange={(event) => setStart(event.target.value)}
          error={e.startsOn} />
      </div>
      {plan && total !== null && (
        <p className="text-sm">
          Order total {formatMoney({ amount: String(total), currency: plan.currency })}:{" "}
          <strong>{formatMoney({ amount: String((total * plan.annualReturnPercent / 1200).toFixed(2)), currency: plan.currency })} a month</strong>.
        </p>
      )}
      <SubmitButton>Add order</SubmitButton>
    </form>
  );
}

/** A dummy downline member, referred by the investor or by another member. */
export function DemoMemberForm({ userId, members, joinedOn }: { userId: string; members: DemoMember[]; joinedOn: string }) {
  const { state, formAction, e, v } = useForm(addDemoMemberAction.bind(null, userId));
  const referrers = [{ value: "", label: "The investor (level 1)" },
    ...members.map((m) => ({ value: m.id, label: `${m.name} (level ${m.level + 1} below them)` }))];
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-3">
        <SelectField label="Referred by" name="parentId" options={referrers} defaultValue={v("parentId", "")} error={e.parentId} />
        <TextField label="Name" name="name" defaultValue={v("name")} error={e.name} />
        <TextField label="Joined" name="joinedOn" type="date" defaultValue={v("joinedOn", joinedOn)} error={e.joinedOn} />
      </div>
      <SubmitButton>Add member</SubmitButton>
    </form>
  );
}

/** A remove button, confirmed first, that shows why if the removal fails. */
function RemoveForm({ action, confirm, label }: { action: () => Promise<FormState>; confirm: string; label: string }) {
  const [state, formAction] = useActionState<FormState, FormData>(() => action(), {});
  return (
    <form action={formAction} className="inline-flex flex-col items-end gap-1">
      <SubmitButton variant="danger" confirm={confirm} className="px-3 py-1.5 text-xs">{label}</SubmitButton>
      {state.error && <span role="alert" className="text-xs text-rose-600">{state.error}</span>}
    </form>
  );
}

export function RemoveDemoOrder({ userId, orderId, orderNumber }: { userId: string; orderId: string; orderNumber: string }) {
  return <RemoveForm action={() => removeDemoOrderAction(userId, orderId)} label="Remove"
    confirm={`Remove dummy order ${orderNumber}? Its returns or commission disappears from the figures.`} />;
}

export function RemoveDemoMember({ userId, memberId, name }: { userId: string; memberId: string; name: string }) {
  return <RemoveForm action={() => removeDemoMemberAction(userId, memberId)} label="Remove"
    confirm={`Remove ${name}, everyone below them, and all their dummy orders?`} />;
}

export function RemoveDemoAccount({ userId, name }: { userId: string; name: string }) {
  return <RemoveForm action={() => removeDemoAccountAction(userId)} label="Stop being a demo account"
    confirm={`Stop ${name} being a demo account? All their dummy orders and downline are deleted, and their pages show their real figures again.`} />;
}
