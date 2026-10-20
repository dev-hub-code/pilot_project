import type { Metadata } from "next";
import { buttonBase, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { AgreementTemplateForm } from "@/features/admin/agreement-form";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { AgreementTemplateView } from "@/types/agreement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Lease agreement" };

export default async function AdminAgreementPage() {
  await requireStaff();
  const view = await authFetch<AgreementTemplateView>("/api/v1/admin/agreement-template");
  const current = view.current;

  return (
    <div className="space-y-6">
      <PageHeader title="Lease agreement"
        description="The agreement each investor receives when finance assigns their container. It is filled in with the investment's details and kept as issued: changing the wording here affects only agreements issued afterwards." />

      <Card title={current ? `Wording in force · version ${current.version}` : "No wording yet"}
        description={current
          ? `Saved ${formatDateTime(current.createdAt)}${current.createdByName ? ` by ${current.createdByName}` : " (as shipped)"}`
          : "Containers cannot be assigned until the agreement has wording."}
        actions={current && (
          <a href="/admin/agreement/sample" className={`${buttonBase} ${buttonVariants.secondary}`}>Sample PDF</a>
        )}>
        <AgreementTemplateForm title={current?.title ?? "Container Lease Agreement"} body={current?.body ?? ""}
          placeholders={view.placeholders} />
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
