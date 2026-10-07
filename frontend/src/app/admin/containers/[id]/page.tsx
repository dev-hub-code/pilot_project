import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { SubmitButton } from "@/components/ui/submit-button";
import { ContainerForm } from "@/features/admin/container-form";
import { ContainerStatusForm, DocumentUploadForm } from "@/features/admin/container-panels";
import { setDocumentVisibilityAction } from "@/features/admin/investment-actions";
import { CONTAINER_STATUS_LABEL, CONTAINER_STATUS_TONE, CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { ContainerDetail } from "@/types/marketplace";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Container" };

export default async function ContainerPage({ params }: PageProps<"/admin/containers/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let detail: ContainerDetail;
  try {
    detail = await authFetch<ContainerDetail>(`/api/v1/admin/containers/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const c = detail.container;
  const editable = can(Permission.INVESTMENT_UPDATE) && c.status !== "RETIRED";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-mono text-3xl font-semibold tracking-tight">{c.containerNumber}</h1>
          <p className="text-muted">{CONTAINER_TYPE_LABEL[c.containerType]} · {c.currentLocation}, {c.locationCountry}</p>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge tone={CONTAINER_STATUS_TONE[c.status]}>{CONTAINER_STATUS_LABEL[c.status]}</StatusBadge>
        </div>
      </div>
      {detail.statusReason && <p className="text-sm text-muted">Status reason: {detail.statusReason}</p>}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <Card title="Documents & photos" description="Only documents marked visible appear in the marketplace.">
            {detail.documents.length === 0 ? (
              <p className="text-sm text-muted">No documents yet. Visible photos are shown on the marketplace for plans of this container type.</p>
            ) : (
              <ul className="divide-y divide-border text-sm">
                {detail.documents.map((doc) => (
                  <li key={doc.documentId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                    <div>
                      <a href={`/admin/containers/${c.id}/documents/${doc.documentId}`} target="_blank" rel="noopener noreferrer"
                        className="font-medium text-gold-text hover:underline">{doc.title}</a>
                      <p className="text-xs text-muted">{humanize(doc.purpose)} · {formatDateTime(doc.uploadedAt)}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <StatusBadge tone={doc.visibleToInvestors ? "success" : "neutral"}>
                        {doc.visibleToInvestors ? "Visible" : "Internal"}
                      </StatusBadge>
                      {editable && (
                        <form action={setDocumentVisibilityAction.bind(null, c.id, doc.documentId, !doc.visibleToInvestors)}>
                          <SubmitButton variant="quiet" pendingLabel="…">{doc.visibleToInvestors ? "Hide" : "Show"}</SubmitButton>
                        </form>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          {editable && <Card title="Edit details"><ContainerForm existing={detail} /></Card>}
        </div>
        {editable && (
          <div className="space-y-6">
            <Card title="Upload"><DocumentUploadForm containerId={c.id} /></Card>
            <Card title="Status">
              {c.status === "RESERVED" || c.status === "ON_LEASE" ? (
                <p className="text-sm text-muted">
                  {c.status === "RESERVED" ? "Reserved for an order awaiting payment." : "Leased to an investor."} Its status changes with
                  the order and the lease.
                </p>
              ) : (
                <ContainerStatusForm containerId={c.id} current={c.status} />
              )}
            </Card>
          </div>
        )}
      </div>
    </div>
  );
}
