import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { assignContainerAction } from "@/features/admin/actions";
import { AssignContainerForm } from "@/features/admin/assign-container-form";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ContainerType, Money } from "@/types/marketplace";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Assign containers" };

interface PendingAssignment {
  holdingId: string;
  orderId: string;
  orderNumber: string | null;
  investorId: string;
  investorName: string | null;
  investorEmail: string | null;
  productCode: string;
  productTitle: string;
  containerType: ContainerType;
  amount: Money;
  tenureMonths: number;
  paidAt: string;
}

interface AssignmentQueue {
  pending: PageResponse<PendingAssignment>;
}

export default async function ContainerAssignmentsPage({ searchParams }: PageProps<"/admin/container-assignments">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.CONTAINER_ASSIGN)) {
    return <Notice tone="warning">Assigning containers needs the Container assign permission.</Notice>;
  }
  const queue = await authFetch<AssignmentQueue>(`/api/v1/admin/container-assignments?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <PageHeader title="Assign containers"
        description="Paid investments waiting for a container, oldest first. Type the number of the container you are assigning; the investor\u2019s lease and monthly payouts start that day, and they are notified." />
      {queue.pending.content.length === 0 ? (
        <EmptyState title="Nothing to assign" description="Every paid investment has its container." />
      ) : (
        <ul className="space-y-4">
          {queue.pending.content.map((p) => {
            const name = p.investorName ?? p.investorEmail ?? "the investor";
            return (
              <li key={p.holdingId} className="grid gap-6 border border-border bg-surface p-6 lg:grid-cols-[1.4fr_1fr]">
                <div className="space-y-2 text-sm">
                  <p className="font-medium">
                    {CONTAINER_TYPE_LABEL[p.containerType]} container · <span className="font-mono">{p.productCode}</span>{" "}
                    <span className="text-muted">{p.productTitle}</span>
                  </p>
                  <p>
                    <Link href={`/admin/users/${p.investorId}`} className="text-gold-text hover:underline">{name}</Link>
                    {p.investorEmail && p.investorName && <span className="text-muted"> · {p.investorEmail}</span>}
                  </p>
                  <p className="text-muted">
                    <Link href={`/admin/orders/${p.orderId}`} className="font-mono hover:underline">{p.orderNumber ?? "Order"}</Link>
                    {" · "}{formatMoney(p.amount)} · {p.tenureMonths}-month lease · paid {formatDateTime(p.paidAt)}
                  </p>
                </div>
                <AssignContainerForm action={assignContainerAction.bind(null, p.holdingId)} investorName={name}
                  typeLabel={CONTAINER_TYPE_LABEL[p.containerType]} />
              </li>
            );
          })}
        </ul>
      )}
      <Pagination page={queue.pending} basePath="/admin/container-assignments" params={{}} />
    </div>
  );
}
