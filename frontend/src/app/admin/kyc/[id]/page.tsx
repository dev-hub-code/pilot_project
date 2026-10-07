import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { approveKycAction, rejectKycAction } from "@/features/admin/actions";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { KycReviewDetail } from "@/types/user";
import { formatDate, formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "KYC review" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function KycReviewPage({ params }: PageProps<"/admin/kyc/[id]">) {
  const { id } = await params;
  if (!UUID.test(id)) notFound();
  const session = await requireStaff();

  let detail: KycReviewDetail;
  try {
    detail = await authFetch<KycReviewDetail>(`/api/v1/admin/kyc/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { submission: s, account } = detail;
  const nameMismatch =
    s.legalFirstName.toLowerCase() !== account.firstName.toLowerCase() ||
    s.legalLastName.toLowerCase() !== account.lastName.toLowerCase();
  const ownSubmission = s.userId === session.userId;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{s.legalFirstName} {s.legalLastName}</h1>
        <StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Evidence" description="Each document you open is recorded in the audit log.">
            <div className="grid gap-4 sm:grid-cols-2">
              {s.documents.map((doc) => {
                const href = `/admin/kyc/${s.id}/documents/${doc.id}`;
                return (
                  <figure key={doc.id} className="space-y-2">
                    <figcaption className="text-sm font-medium">{humanize(doc.purpose.replace("KYC_", ""))}</figcaption>
                    <a href={href} target="_blank" rel="noopener noreferrer"
                      className="block border border-border bg-background p-3 text-sm text-brand hover:underline">
                      Open document in a new tab
                    </a>
                  </figure>
                );
              })}
            </div>
          </Card>

          <Card title="Submitted details">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Document">{humanize(s.documentType)} {s.documentNumberMasked}</Item>
              <Item label="Issued by">{s.documentIssuingCountry}</Item>
              <Item label="Expires">{formatDate(s.documentExpiryDate)}</Item>
              <Item label="Date of birth">{formatDate(s.dateOfBirth)}</Item>
              <Item label="Nationality">{s.nationality}</Item>
              <Item label="Submitted">{formatDateTime(s.submittedAt)}</Item>
              <Item label="Account">
                <Link className="text-brand hover:underline" href={`/admin/users/${account.id}`}>
                  {account.firstName} {account.lastName} ({account.email})
                </Link>
              </Item>
              {s.reviewedAt && <Item label="Reviewed">{formatDateTime(s.reviewedAt)}</Item>}
              {s.rejectionReason && <Item label="Rejection reason">{s.rejectionReason}</Item>}
            </dl>
            {nameMismatch && (
              <div className="mt-4">
                <Notice tone="warning">The legal name differs from the account name. Check the document carefully.</Notice>
              </div>
            )}
          </Card>
        </div>

        {s.status === "PENDING" && (
          <Card title="Decision">
            {ownSubmission ? (
              <Notice tone="warning">You cannot review your own verification.</Notice>
            ) : (
              <div className="space-y-6">
                <ConfirmForm action={approveKycAction.bind(null, s.id)} submitLabel="Approve identity"
                  confirm="Approve this identity verification?" />
                <ReasonForm action={rejectKycAction.bind(null, s.id)} label="Rejection reason (shown to the investor)"
                  submitLabel="Reject" />
              </div>
            )}
          </Card>
        )}
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted">{label}</dt>
      <dd className="mt-0.5 font-medium">{children}</dd>
    </div>
  );
}
