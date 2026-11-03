import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { approveKycAction, assignVerificationAction, rejectKycAction, unassignVerificationAction } from "@/features/admin/actions";
import { BankAccountReview } from "@/features/admin/bank-account-review";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { Permission, hasPermission } from "@/lib/permissions";
import type { Address, AdminBankAccount, Assignee, KycReviewDetail } from "@/types/user";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import { formatAadhaar, KYC_DOCUMENT_LABEL, kycNumbers } from "@/features/profile/kyc-labels";
import { formatDate, formatDateTime, formatDayMonthYear, humanize } from "@/utils/format";
import { EmailText } from "@/components/ui/email-text";

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
  const { submission: s, account, address } = detail;
  const nameMismatch =
    s.legalFirstName.toLowerCase() !== account.firstName.toLowerCase() ||
    s.legalLastName.toLowerCase() !== account.lastName.toLowerCase();
  const ownSubmission = s.userId === session.userId;
  const canVerifyBanks = hasPermission(session.permissions, Permission.BANK_ACCOUNT_VERIFY);
  const banks = canVerifyBanks ? await authFetch<AdminBankAccount[]>(`/api/v1/admin/users/${s.userId}/bank-accounts`) : [];
  const identityApproved = s.status === "APPROVED";

  // One Assign to me covers the review and the investor's bank accounts waiting: whatever nobody else holds.
  const openBanks = ownSubmission ? [] : banks.filter((b) => b.account.status === "PENDING_VERIFICATION");
  const openKyc = s.status === "PENDING" && !ownSubmission;
  const assignees = [...(openKyc ? [s.assignee] : []), ...openBanks.map((b) => b.assignee)];
  const someoneFree = assignees.some((a) => !a);
  const held = (assignee: Assignee | null) => assignee?.id === session.userId;
  const assignable = (assignee: Assignee | null) => !assignee || held(assignee);
  const shownAssignee = someoneFree ? null : assignees.find(held) ?? assignees[0] ?? null;
  const kycIf = (want: (a: Assignee | null) => boolean) => (openKyc && want(s.assignee) ? s.id : null);
  const banksIf = (want: (a: Assignee | null) => boolean) => openBanks.filter((b) => want(b.assignee)).map((b) => b.account.id);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{s.legalFirstName} {s.legalLastName}</h1>
        <StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Submitted details">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              {s.aadhaarNumber && <Item label="Aadhaar number"><span className="font-mono text-base font-bold tracking-wide">{formatAadhaar(s.aadhaarNumber)}</span></Item>}
              {s.panNumber && <Item label="PAN"><span className="font-mono text-base font-bold tracking-wide">{s.panNumber}</span></Item>}
              {s.documentType && (
                <>
                  <Item label="Document"><span className="font-mono text-base font-bold tracking-wide">{kycNumbers(s)}</span></Item>
                  <Item label="Issued by">{s.documentIssuingCountry}</Item>
                  <Item label="Expires">{formatDate(s.documentExpiryDate)}</Item>
                </>
              )}
              <Item label="Date of birth">{formatDayMonthYear(s.dateOfBirth)}</Item>
              <Item label="Nationality">{s.nationality}</Item>
              <Item label="Address (from profile)" className="sm:col-span-2">
                {address?.line1 ? <AddressLines address={address} /> : <span className="text-muted">Not given</span>}
              </Item>
              <Item label="Submitted">{formatDateTime(s.submittedAt)}</Item>
              <Item label="Account">
                <Link className="text-brand hover:underline" href={`/admin/users/${account.id}`}>
                  {account.firstName} {account.lastName} (<EmailText>{account.email}</EmailText>)
                </Link>
              </Item>
              {s.status !== "PENDING" && s.assignee && <Item label="Reviewed by">{s.assignee.name}</Item>}
              {s.reviewedAt && <Item label="Reviewed">{formatDateTime(s.reviewedAt)}</Item>}
              {s.rejectionReason && <Item label="Rejection reason">{s.rejectionReason}</Item>}
            </dl>
            {nameMismatch && (
              <div className="mt-4">
                <Notice tone="warning">The legal name differs from the account name. Check the document carefully.</Notice>
              </div>
            )}
          </Card>

          <Card title="Evidence" description="Each document you open is recorded in the audit log.">
            <div className="grid gap-4 sm:grid-cols-2">
              {s.documents.map((doc) => {
                const href = `/admin/kyc/${s.id}/documents/${doc.id}`;
                return (
                  <figure key={doc.id} className="space-y-2">
                    <figcaption className="text-sm font-medium">{KYC_DOCUMENT_LABEL[doc.purpose]}</figcaption>
                    <a href={href} target="_blank" rel="noopener noreferrer"
                      className="block border border-border bg-background p-3 text-sm text-brand hover:underline">
                      Open document in a new tab
                    </a>
                  </figure>
                );
              })}
            </div>
          </Card>

          {canVerifyBanks && (
            <Card title="Bank verification" description="Compare the holder name with the verified identity before approving payouts.">
              {banks.length === 0 ? (
                <p className="text-sm text-muted">This investor has not added a bank account yet.</p>
              ) : (
                <div className="space-y-6">
                  {!identityApproved && banks.some((b) => b.account.status === "PENDING_VERIFICATION") && (
                    <Notice tone="warning">Approve the identity first: a bank account can only be verified after that.</Notice>
                  )}
                  {banks.map((bank) => (
                    <BankAccountReview key={bank.account.id} item={bank} viewerId={session.userId} canVerify={identityApproved} sharedAssignment />
                  ))}
                </div>
              )}
            </Card>
          )}
        </div>

        {ownSubmission && s.status === "PENDING" ? (
          <Card title="Decision">
            <Notice tone="warning">You cannot review your own verification.</Notice>
          </Card>
        ) : assignees.length > 0 && (
          <Card title="Decision">
            <ReviewAssignment assignee={shownAssignee} viewerId={session.userId}
              assign={assignVerificationAction.bind(null, kycIf(assignable), banksIf(assignable))}
              unassign={unassignVerificationAction.bind(null, kycIf(held), banksIf(held))}>
              {openKyc && held(s.assignee) ? (
                <>
                  <ConfirmForm action={approveKycAction.bind(null, s.id)} submitLabel="Approve identity"
                    confirm="Approve this identity verification?" />
                  <ReasonForm action={rejectKycAction.bind(null, s.id)} label="Rejection reason (shown to the investor)"
                    submitLabel="Reject" />
                </>
              ) : null}
            </ReviewAssignment>
          </Card>
        )}
      </div>
    </div>
  );
}

function Item({ label, children, className }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={className}>
      <dt className="text-muted">{label}</dt>
      <dd className="mt-0.5 font-medium">{children}</dd>
    </div>
  );
}

/** The address as it would be written on an envelope, the country by name. */
function AddressLines({ address }: { address: Address }) {
  const country = COUNTRY_OPTIONS.find((c) => c.value === address.country)?.label ?? address.country;
  const lines = [
    address.line1,
    address.line2,
    [address.city, address.stateRegion, address.postalCode].filter(Boolean).join(", "),
    country,
  ].filter(Boolean);
  return <span className="block">{lines.map((line) => <span key={line} className="block">{line}</span>)}</span>;
}
