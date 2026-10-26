import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import {
  assignContainerAction, generateContainerNumberAction, releaseContainerTaskAction, takeContainerTaskAction,
} from "@/features/admin/actions";
import { AssignContainerForm } from "@/features/admin/assign-container-form";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ContainerType, Money } from "@/types/marketplace";
import type { Assignee } from "@/types/user";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { EmailText } from "@/components/ui/email-text";

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
  /** The finance member who took the task; only they may assign the container. */
  assignee: Assignee | null;
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
  const canViewOrders = hasPermission(session.permissions, Permission.ORDER_VIEW);
  const queue = await authFetch<AssignmentQueue>(`/api/v1/admin/container-assignments?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <PageHeader title="Assign containers"
        description="Paid investments waiting for a container, oldest first. Assign one to yourself, then type the number of the container you are assigning (or generate a new one); the investor\u2019s lease starts at the next full hour and rent is credited every hour from then, and they are notified." />
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
                    {p.investorEmail && p.investorName && <span className="text-muted"> · <EmailText>{p.investorEmail}</EmailText></span>}
                  </p>
                  <p className="text-muted">
                    {canViewOrders ? (
                      <Link href={`/admin/orders/${p.orderId}`} className="font-mono hover:underline">{p.orderNumber ?? "Order"}</Link>
                    ) : (
                      <span className="font-mono">{p.orderNumber ?? "Order"}</span>
                    )}
                    {" · "}{formatMoney(p.amount)} · {p.tenureMonths}-month lease · paid {formatDateTime(p.paidAt)}
                  </p>
                </div>
                <ReviewAssignment assignee={p.assignee} viewerId={session.userId}
                  assign={takeContainerTaskAction.bind(null, p.holdingId)}
                  unassign={releaseContainerTaskAction.bind(null, p.holdingId)}>
                  <AssignContainerForm action={assignContainerAction.bind(null, p.holdingId)} generate={generateContainerNumberAction}
                    investorName={name}
                    typeLabel={CONTAINER_TYPE_LABEL[p.containerType]} />
                </ReviewAssignment>
              </li>
            );
          })}
        </ul>
      )}
      <Pagination page={queue.pending} basePath="/admin/container-assignments" params={{}} />
    </div>
  );
}
