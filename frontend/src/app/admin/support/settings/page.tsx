import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfigForm } from "@/features/admin/config-form";
import {
  saveAssignmentAction, saveCalendarAction, saveCategoryAction, saveLevelAction, savePolicyAction, saveRuleAction,
} from "@/features/admin/support-settings-actions";
import { SupportShell, supportAbilities } from "@/features/admin/support-shell";
import {
  CATEGORY_LABEL, CATEGORY_ORDER, CHANNEL_LABEL, PRIORITY_LABEL, PRIORITY_ORDER, STAFF_STATUS_LABEL, STATUS_ORDER,
} from "@/features/support/labels";
import { Permission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type {
  Agent, AssignmentRule, AutomationAction, AutomationRule, AutomationRun, AutomationTrigger, EscalationLevel, SlaPolicy,
  SupportCalendar, SupportCategory, Workload,
} from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Support settings" };

const TABS = [
  { key: "sla", label: "SLA policies" },
  { key: "calendar", label: "Business hours" },
  { key: "categories", label: "Categories" },
  { key: "assignment", label: "Assignment" },
  { key: "escalation", label: "Escalation" },
  { key: "automation", label: "Automation" },
] as const;
type Tab = (typeof TABS)[number]["key"];

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const input = "w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold disabled:opacity-70";
const TRIGGERS: Record<AutomationTrigger, string> = {
  TICKET_CREATED: "A ticket is created",
  UNASSIGNED_FOR: "A ticket stays unassigned for N minutes",
  SLA_PERCENT: "An SLA target is N% used",
  SLA_BREACHED: "An SLA target is breached",
  CUSTOMER_REPLIED_RESOLVED: "The customer replies to a resolved ticket",
  TICKET_RESOLVED: "A ticket is resolved",
  TICKET_CLOSED: "A ticket is closed",
  TICKET_REOPENED: "A ticket is reopened",
};
const ACTIONS: Record<AutomationAction, string> = {
  NOTIFY_ASSIGNEE: "Notify the assignee",
  NOTIFY_TEAM: "Notify the ticket's team",
  NOTIFY_ESCALATION_CONTACTS: "Notify the escalation contacts",
  AUTO_ASSIGN: "Assign automatically",
  ESCALATE: "Escalate",
  SET_PRIORITY: "Set the priority",
  REQUEST_FEEDBACK: "Ask the customer for a rating",
  RECORD_RESOLUTION_METRICS: "Record resolution metrics",
};
const hours = (m: number | null) => (m === null ? "" : String(Math.round((m / 60) * 100) / 100));

export default async function SupportSettingsPage({ searchParams }: PageProps<"/admin/support/settings">) {
  const denied = await deniedUnless(Permission.SUPPORT_CONFIG_MANAGE, Permission.SYSTEM_SETTINGS_MANAGE, Permission.SUPPORT_TICKET_VIEW);
  if (denied) return denied;
  const can = supportAbilities((await requireStaff()).permissions);
  const requested = (await searchParams).tab;
  const tab: Tab = TABS.find((t) => t.key === requested)?.key ?? "sla";
  return (
    <SupportShell active="settings">
      <PageHeader title="Support settings" description={can.configure
        ? "SLA targets, business hours, categories, assignment, escalation and automation. Every change is audited."
        : "How tickets are routed, timed and escalated. Read-only: changing these needs the support configuration permission."} />
      <nav aria-label="Settings sections" className="flex flex-wrap gap-2 text-sm">
        {TABS.map((t) => (
          <Link key={t.key} href={`/admin/support/settings?tab=${t.key}`} aria-current={t.key === tab ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${t.key === tab ? "border-foreground" : "border-border text-muted"}`}>{t.label}</Link>
        ))}
      </nav>
      {tab === "sla" && <SlaTab edit={can.configure} />}
      {tab === "calendar" && <CalendarTab edit={can.configure} />}
      {tab === "categories" && <CategoriesTab edit={can.configure} />}
      {tab === "assignment" && <AssignmentTab edit={can.configure} />}
      {tab === "escalation" && <EscalationTab edit={can.configure} />}
      {tab === "automation" && <AutomationTab edit={can.configure} />}
    </SupportShell>
  );
}

/** A form for editors; the same fields, disabled, for readers. */
function Editable({ edit, action, submitLabel, children }: {
  edit: boolean;
  action: Parameters<typeof ConfigForm>[0]["action"];
  submitLabel?: string;
  children: ReactNode;
}) {
  return edit ? <ConfigForm action={action} submitLabel={submitLabel}>{children}</ConfigForm>
    : <fieldset disabled className="space-y-4">{children}</fieldset>;
}

function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="block space-y-1 text-sm">
      <span className="block font-medium">{label}</span>
      {children}
      {hint && <span className="block text-xs text-muted">{hint}</span>}
    </label>
  );
}

function Check({ name, label, checked }: { name: string; label: string; checked: boolean }) {
  return (
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" name={name} defaultChecked={checked} className="size-4" />{label}</label>
  );
}

function Options({ name, value, options, any = "Any" }: { name: string; value: string | null; options: [string, string][]; any?: string | null }) {
  return (
    <select name={name} defaultValue={value ?? ""} className={input}>
      {any !== null && <option value="">{any}</option>}
      {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
    </select>
  );
}

/** Staff who can handle tickets: the agent list when the viewer may see it, else the workload list. */
async function staff(): Promise<{ id: string; name: string }[]> {
  try {
    return (await authFetch<Agent[]>("/api/v1/admin/support/agents")).map((a) => ({ id: a.userId, name: a.name }));
  } catch {
    try {
      return (await authFetch<Workload[]>("/api/v1/admin/support/workload")).map((w) => ({ id: w.agentId, name: w.name }));
    } catch {
      return [];
    }
  }
}

// ------------------------------------------------------------------------------------------------------ SLA

function PolicyFields({ p }: { p: SlaPolicy | null }) {
  return (
    <>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Name"><input name="name" defaultValue={p?.name} maxLength={100} className={input} /></Field>
        <Field label="Priority"><Options name="priority" value={p?.priority ?? null} options={PRIORITY_ORDER.map((x) => [x, PRIORITY_LABEL[x]])} /></Field>
        <Field label="Category"><Options name="category" value={p?.category ?? null} options={CATEGORY_ORDER.map((x) => [x, CATEGORY_LABEL[x]])} /></Field>
        <Field label="Customer type"><Options name="customerType" value={p?.customerType ?? null} options={[["NETWORK", "Network investors"], ["ONLINE", "Online investors"]]} /></Field>
        <Field label="First response (hours)"><input name="firstResponseHours" inputMode="decimal" defaultValue={hours(p?.firstResponseMinutes ?? null)} className={input} /></Field>
        <Field label="Next response (hours)" hint="After each customer reply; empty for none"><input name="nextResponseHours" inputMode="decimal" defaultValue={hours(p?.nextResponseMinutes ?? null)} className={input} /></Field>
        <Field label="Resolution (hours)"><input name="resolutionHours" inputMode="decimal" defaultValue={hours(p?.resolutionMinutes ?? null)} className={input} /></Field>
      </div>
      <div className="flex flex-wrap gap-5">
        <Check name="businessHours" label="Count business hours only" checked={p?.businessHours ?? true} />
        <Check name="pauseWhileWaiting" label="Pause resolution while waiting on the customer" checked={p?.pauseWhileWaiting ?? true} />
        <Check name="active" label="Active" checked={p?.active ?? true} />
      </div>
    </>
  );
}

async function SlaTab({ edit }: { edit: boolean }) {
  const policies = await authFetch<SlaPolicy[]>(`/api/v1/admin/support/config/sla-policies`);
  return (
    <div className="space-y-4">
      <Notice>The most specific active policy matching a ticket&apos;s priority, category and customer type applies. Targets are re-set when a ticket&apos;s priority or category changes.</Notice>
      {policies.map((p) => (
        <Card key={p.id} title={p.name} description={`${[p.priority && PRIORITY_LABEL[p.priority], p.category && CATEGORY_LABEL[p.category], p.customerType && (p.customerType === "NETWORK" ? "Network" : "Online")].filter(Boolean).join(" · ") || "Every ticket"}${p.active ? "" : " · inactive"} · updated ${formatDateTime(p.updatedAt)}`}>
          <Editable edit={edit} action={savePolicyAction.bind(null, p.id)}><PolicyFields p={p} /></Editable>
        </Card>
      ))}
      {edit && <Card title="Add a policy"><Editable edit action={savePolicyAction.bind(null, null)} submitLabel="Add policy"><PolicyFields p={null} /></Editable></Card>}
    </div>
  );
}

// ------------------------------------------------------------------------------------------------- calendar

async function CalendarTab({ edit }: { edit: boolean }) {
  const cal = await authFetch<SupportCalendar>("/api/v1/admin/support/config/calendar");
  return (
    <Card title="Business hours" description={`In ${cal.timeZone}. SLA targets on business-hours policies only count time inside these hours, skipping holidays.`}>
      <Editable edit={edit} action={saveCalendarAction}>
        <div className="space-y-2">
          {DAYS.map((name, i) => {
            const d = i + 1;
            const h = cal.hours.find((x) => x.dayOfWeek === d);
            return (
              <div key={d} className="grid grid-cols-[8rem_1fr_1fr] items-center gap-3 text-sm sm:grid-cols-[10rem_9rem_9rem]">
                <Check name={`day${d}`} label={name} checked={Boolean(h)} />
                <input type="time" name={`opens${d}`} aria-label={`${name} opens`} defaultValue={h?.opensAt.slice(0, 5) ?? "09:00"} className={input} />
                <input type="time" name={`closes${d}`} aria-label={`${name} closes`} defaultValue={h?.closesAt.slice(0, 5) ?? "18:00"} className={input} />
              </div>
            );
          })}
        </div>
        <Field label="Holidays" hint="One per line: YYYY-MM-DD Name">
          <textarea name="holidays" rows={8} defaultValue={cal.holidays.map((x) => `${x.day} ${x.name}`).join("\n")} className={`${input} font-mono`} />
        </Field>
      </Editable>
    </Card>
  );
}

// ----------------------------------------------------------------------------------------------- categories

function CategoryFields({ c, isNew }: { c: SupportCategory | null; isNew: boolean }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
      {isNew && <Field label="Code"><input name="code" maxLength={40} placeholder="CARD_PAYMENT" className={`${input} font-mono uppercase`} /></Field>}
      <Field label="Name"><input name="name" maxLength={80} defaultValue={c?.name} className={input} /></Field>
      <div className="lg:col-span-2"><Field label="Description"><input name="description" maxLength={255} defaultValue={c?.description ?? ""} className={input} /></Field></div>
      <Field label="Default priority"><Options name="defaultPriority" value={c?.defaultPriority ?? null} any="None" options={PRIORITY_ORDER.map((x) => [x, PRIORITY_LABEL[x]])} /></Field>
      <Field label="Default team"><Options name="defaultTeam" value={c?.defaultTeam ?? null} any="Support (default)" options={[["SUPPORT", "Support"], ["FINANCE", "Finance"]]} /></Field>
      <Field label="Order"><input name="sortOrder" type="number" min={0} max={10000} defaultValue={c?.sortOrder ?? 0} className={input} /></Field>
      {!isNew && <div className="flex items-end pb-2"><Check name="active" label="Active" checked={c?.active ?? true} /></div>}
    </div>
  );
}

async function CategoriesTab({ edit }: { edit: boolean }) {
  const all = await authFetch<SupportCategory[]>("/api/v1/admin/support/config/categories");
  const tops = all.filter((c) => c.parentId === null).sort((a, b) => a.sortOrder - b.sortOrder);
  return (
    <div className="space-y-4">
      <Notice>Top-level categories are fixed (tickets, reports and finance routing use them); their names, defaults and subcategories can be changed. A category&apos;s default priority and team apply to new tickets.</Notice>
      {tops.map((top) => {
        const subs = all.filter((c) => c.parentId === top.id).sort((a, b) => a.sortOrder - b.sortOrder);
        return (
          <details key={top.id} className="group rounded-xl border border-border bg-surface p-4 shadow-sm">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-3">
              <span className="font-medium">{top.name} <span className="ml-2 text-xs text-muted">{subs.length} subcategories</span></span>
              <span aria-hidden className="text-muted transition-transform group-open:rotate-90">›</span>
            </summary>
            <div className="mt-4 space-y-5">
              <Editable edit={edit} action={saveCategoryAction.bind(null, top.id, null, top.category)}><CategoryFields c={top} isNew={false} /></Editable>
              {subs.map((s) => (
                <div key={s.id} className="border-l-2 border-border pl-4">
                  <p className="mb-2 font-mono text-xs text-muted">{s.code}</p>
                  <Editable edit={edit} action={saveCategoryAction.bind(null, s.id, top.id, top.category)}><CategoryFields c={s} isNew={false} /></Editable>
                </div>
              ))}
              {edit && (
                <div className="border-l-2 border-gold pl-4">
                  <p className="mb-2 text-sm font-medium">Add a subcategory</p>
                  <Editable edit action={saveCategoryAction.bind(null, null, top.id, top.category)} submitLabel="Add"><CategoryFields c={null} isNew /></Editable>
                </div>
              )}
            </div>
          </details>
        );
      })}
    </div>
  );
}

// ----------------------------------------------------------------------------------------------- assignment

async function AssignmentTab({ edit }: { edit: boolean }) {
  const [rules, agents] = await Promise.all([
    authFetch<AssignmentRule[]>("/api/v1/admin/support/config/assignment"),
    staff(),
  ]);
  return (
    <div className="space-y-4">
      <Notice>New tickets in a category are assigned by its rule: manually (left in the queue), round-robin, or to the agent with the fewest open tickets. Only agents who already handle tickets can be chosen; no roles change.</Notice>
      <div className="grid gap-4 lg:grid-cols-2">
        {CATEGORY_ORDER.map((cat) => {
          const rule = rules.find((r) => r.category === cat);
          const members = new Set(rule?.members.map((m) => m.userId));
          const pool = [...agents, ...(rule?.members ?? []).filter((m) => !agents.some((a) => a.id === m.userId)).map((m) => ({ id: m.userId, name: m.name }))];
          return (
            <Card key={cat} title={CATEGORY_LABEL[cat]} description={rule ? `Updated ${formatDateTime(rule.updatedAt)}` : undefined}>
              <Editable edit={edit} action={saveAssignmentAction.bind(null, cat)}>
                <Field label="Strategy">
                  <Options name="strategy" value={rule?.strategy ?? "MANUAL"} any={null}
                    options={[["MANUAL", "Manual: stays in the queue"], ["ROUND_ROBIN", "Round-robin"], ["LEAST_LOADED", "Least-loaded"]]} />
                </Field>
                <fieldset className="space-y-1">
                  <legend className="text-sm font-medium">Agents</legend>
                  {pool.length === 0 ? <p className="text-xs text-muted">No agents available to list.</p> : (
                    <div className="grid gap-1 sm:grid-cols-2">
                      {pool.map((a) => <Check key={a.id} name="members" label={a.name} checked={members.has(a.id)} />)}
                    </div>
                  )}
                </fieldset>
              </Editable>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------------------------------- escalation

async function EscalationTab({ edit }: { edit: boolean }) {
  const [levels, agents] = await Promise.all([
    authFetch<EscalationLevel[]>("/api/v1/admin/support/config/escalation-levels"),
    staff(),
  ]);
  return (
    <div className="space-y-4">
      <Notice>
        Tickets escalate by hand, through automation rules (for example when an SLA target is breached), and automatically when a critical
        ticket is not acknowledged in time, when the customer follows up repeatedly, or when a ticket is reopened repeatedly. Each step
        raises the level by one and tells that level&apos;s contacts.
      </Notice>
      <div className="grid gap-4 lg:grid-cols-2">
        {levels.map((l) => {
          const contacts = new Set(l.contacts.map((c) => c.userId));
          const pool = [...agents, ...l.contacts.filter((c) => !agents.some((a) => a.id === c.userId)).map((c) => ({ id: c.userId, name: c.name }))];
          return (
            <Card key={l.level} title={`Level ${l.level}`}>
              <Editable edit={edit} action={saveLevelAction.bind(null, l.level)}>
                <Field label="Name"><input name="name" maxLength={60} defaultValue={l.name} className={input} /></Field>
                <Field label="Description"><input name="description" maxLength={255} defaultValue={l.description ?? ""} className={input} /></Field>
                <fieldset className="space-y-1">
                  <legend className="text-sm font-medium">Contacts notified</legend>
                  {pool.length === 0 ? <p className="text-xs text-muted">No staff available to list.</p> : (
                    <div className="grid gap-1 sm:grid-cols-2">{pool.map((a) => <Check key={a.id} name="contacts" label={a.name} checked={contacts.has(a.id)} />)}</div>
                  )}
                </fieldset>
              </Editable>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------------------------------- automation

function Multi({ name, legend, options, chosen }: { name: string; legend: string; options: [string, string][]; chosen: unknown }) {
  const set = new Set(Array.isArray(chosen) ? chosen.map(String) : []);
  return (
    <fieldset className="space-y-1">
      <legend className="text-sm font-medium">{legend} <span className="font-normal text-muted">(none ticked: any)</span></legend>
      <div className="flex flex-wrap gap-x-4 gap-y-1">{options.map(([v, l]) => <Check key={v} name={name} label={l} checked={set.has(v)} />)}</div>
    </fieldset>
  );
}

function RuleFields({ r }: { r: AutomationRule | null }) {
  const c = r?.conditions ?? {};
  const p = r?.parameters ?? {};
  const num = (v: unknown) => (typeof v === "number" ? String(v) : "");
  return (
    <>
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Name"><input name="name" maxLength={120} defaultValue={r?.name} className={input} /></Field>
        <Field label="When"><Options name="trigger" value={r?.trigger ?? "TICKET_CREATED"} any={null} options={Object.entries(TRIGGERS)} /></Field>
        <Field label="Then"><Options name="action" value={r?.action ?? "NOTIFY_TEAM"} any={null} options={Object.entries(ACTIONS)} /></Field>
      </div>
      <details className="rounded-lg border border-border p-3" open={!r}>
        <summary className="cursor-pointer text-sm font-medium">Conditions and settings</summary>
        <div className="mt-3 space-y-3">
          <Multi name="priorities" legend="Priorities" chosen={c.priorities} options={PRIORITY_ORDER.map((x) => [x, PRIORITY_LABEL[x]])} />
          <Multi name="categories" legend="Categories" chosen={c.categories} options={CATEGORY_ORDER.map((x) => [x, CATEGORY_LABEL[x]])} />
          <Multi name="statuses" legend="Statuses" chosen={c.statuses} options={STATUS_ORDER.map((x) => [x, STAFF_STATUS_LABEL[x]])} />
          <Multi name="teams" legend="Teams" chosen={c.teams} options={[["SUPPORT", "Support"], ["FINANCE", "Finance"]]} />
          <Multi name="channels" legend="Channels" chosen={c.channels} options={(["PORTAL", "STAFF", "SYSTEM", "EMAIL"] as const).map((x) => [x, CHANNEL_LABEL[x]])} />
          <Multi name="metrics" legend="SLA targets (SLA triggers)" chosen={c.metrics}
            options={[["FIRST_RESPONSE", "First response"], ["NEXT_RESPONSE", "Next response"], ["RESOLUTION", "Resolution"]]} />
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <Field label="Assigned?"><Options name="unassigned" value={c.unassigned === undefined ? null : String(c.unassigned)} options={[["true", "Unassigned only"], ["false", "Assigned only"]]} /></Field>
            <Field label="Up to escalation level"><input name="maxEscalationLevel" type="number" min={0} max={3} defaultValue={num(c.maxEscalationLevel)} className={input} /></Field>
            <Field label="Minutes" hint="Unassigned-for trigger"><input name="minutes" type="number" min={1} defaultValue={num(c.minutes)} className={input} /></Field>
            <Field label="Percent used" hint="SLA % trigger, 1–99"><input name="percent" type="number" min={1} max={99} defaultValue={num(c.percent)} className={input} /></Field>
            <Field label="Set priority to" hint="Set-priority action"><Options name="priority" value={(p.priority as string) ?? null} any="—" options={PRIORITY_ORDER.map((x) => [x, PRIORITY_LABEL[x]])} /></Field>
            <Field label="Escalate to level" hint="Empty: next level"><input name="level" type="number" min={1} max={4} defaultValue={num(p.level)} className={input} /></Field>
            <Field label="Assign by" hint="Auto-assign action"><Options name="strategy" value={(p.strategy as string) ?? null} any="Least-loaded" options={[["LEAST_LOADED", "Least-loaded"], ["ROUND_ROBIN", "Round-robin"]]} /></Field>
          </div>
        </div>
      </details>
      <Check name="enabled" label="Enabled" checked={r?.enabled ?? true} />
    </>
  );
}

async function AutomationTab({ edit }: { edit: boolean }) {
  const [rules, runs] = await Promise.all([
    authFetch<AutomationRule[]>("/api/v1/admin/support/config/automation-rules"),
    authFetch<AutomationRun[]>("/api/v1/admin/support/config/automation-runs"),
  ]);
  return (
    <div className="space-y-4">
      <Notice>Rules pick from a fixed list of events, conditions and actions; nothing is ever run as code or SQL. Each rule runs at most once per ticket and event, and a failed run is retried up to three times.</Notice>
      {rules.map((r) => (
        <Card key={r.id} title={r.name} description={`${TRIGGERS[r.trigger]} → ${ACTIONS[r.action]} · ${r.runs} runs, ${r.failures} failed${r.lastRunAt ? ` · last ${formatDateTime(r.lastRunAt)}` : ""}${r.enabled ? "" : " · disabled"}`}>
          <Editable edit={edit} action={saveRuleAction.bind(null, r.id)}><RuleFields r={r} /></Editable>
        </Card>
      ))}
      {edit && <Card title="Add a rule"><Editable edit action={saveRuleAction.bind(null, null)} submitLabel="Add rule"><RuleFields r={null} /></Editable></Card>}
      <Card title="Recent runs">
        {runs.length === 0 ? <p className="text-sm text-muted">No runs yet.</p> : (
          <DataTable columns={["Rule", "Ticket", "Result", "Attempts", "Error", "When"]}>
            {runs.map((x) => (
              <tr key={x.id}>
                <Cell>{x.ruleName}</Cell>
                <Cell><Link href={`/admin/support/${x.ticketId}`} className="font-mono text-gold-text hover:underline">{x.reference}</Link></Cell>
                <Cell><StatusBadge tone={x.status === "SUCCEEDED" ? "success" : x.status === "FAILED" ? "danger" : "neutral"}>{x.status.charAt(0) + x.status.slice(1).toLowerCase()}</StatusBadge></Cell>
                <Cell className="tabular-nums">{x.attempts}</Cell>
                <Cell wrap className="max-w-xs text-muted">{x.lastError ?? "—"}</Cell>
                <Cell className="text-muted">{formatDateTime(x.updatedAt)}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>
    </div>
  );
}
