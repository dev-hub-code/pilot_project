import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { TermsForm } from "@/features/admin/terms-form";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { TermsView } from "@/types/terms";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Terms & conditions" };

export default async function AdminTermsPage() {
  await requireStaff();
  const view = await authFetch<TermsView>("/api/v1/admin/terms");
  const current = view.current;

  return (
    <div className="space-y-6">
      <PageHeader title="Terms & conditions"
        description="The terms everyone accepts when they create an account, shown on the public Terms & conditions page. Saving makes a new version; each account keeps the version it accepted." />

      <Card title={current ? `Terms in force · version ${current.version}` : "No terms yet"}
        description={current
          ? `Saved ${formatDateTime(current.createdAt)}${current.createdByName ? ` by ${current.createdByName}` : " (as shipped)"} · accepted by ${view.accepted} account${view.accepted === 1 ? "" : "s"}`
          : "No one can sign up until the terms have wording."}
        actions={<Link href="/terms" target="_blank" className="text-sm underline underline-offset-4">View page</Link>}>
        <TermsForm title={current?.title ?? "Terms and Conditions"} body={current?.body ?? ""} />
      </Card>

      {view.history.length > 1 && (
        <section className="space-y-3" aria-label="Versions">
          <h2 className="text-lg font-semibold tracking-tight">Earlier versions</h2>
          <DataTable columns={["Version", "Title", "Saved", "By"]}>
            {view.history.slice(1).map((t) => (
              <tr key={t.id}>
                <Cell className="tabular-nums">{t.version}</Cell>
                <Cell>{t.title}</Cell>
                <Cell className="text-muted">{formatDateTime(t.createdAt)}</Cell>
                <Cell className="text-muted">{t.createdByName ?? "As shipped"}</Cell>
              </tr>
            ))}
          </DataTable>
        </section>
      )}
    </div>
  );
}
