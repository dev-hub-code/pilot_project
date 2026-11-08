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
import { PortPhoto } from "@/components/brand/port-photo";
import { DetailTile } from "@/components/ui/detail-tile";
import {
  BadgeCheck, CalendarDays, Clock, CreditCard, ExternalLink, FileText, Fingerprint, Flag, Globe, IdCard, MapPin,
  MessageSquareWarning, ShieldCheck, TriangleAlert, UserCheck, UserRound,
} from "lucide-react";

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

  const initials = (s.legalFirstName.charAt(0) + s.legalLastName.charAt(0)).toUpperCase() || "?";

  return (
    <div className="space-y-6">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="kyc-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="yard-stacks" alt="" sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_55%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="flex flex-wrap items-center gap-5 p-6 sm:gap-6 sm:p-8">
          <span aria-hidden="true"
            className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-[linear-gradient(140deg,var(--gold),var(--gold-on-ink))] font-display text-2xl font-semibold text-on-gold shadow-lg ring-4 ring-on-ink/15">
            {initials}
          </span>
          <div className="min-w-0 flex-1 space-y-1.5">
            <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Identity review</p>
            <h1 id="kyc-title" className="flex flex-wrap items-center gap-3 text-2xl font-semibold tracking-tight sm:text-3xl">
              <span className="min-w-0 break-words">{s.legalFirstName} {s.legalLastName}</span>
              <StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge>
            </h1>
            <p className="text-sm text-on-ink/70">
              <Link href={`/admin/users/${account.id}`} className="inline-flex min-w-0 items-center gap-1.5 hover:text-gold-on-ink hover:underline">
                <UserRound aria-hidden="true" className="size-4 shrink-0" />
                <span className="break-all">{account.firstName} {account.lastName} · <EmailText>{account.email}</EmailText></span>
              </Link>
            </p>
          </div>
          <dl className="grid w-full grid-cols-2 gap-2 sm:w-auto sm:gap-3">
            <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 backdrop-blur-md">
              <dt className="text-[11px] text-on-ink/60">Submitted</dt>
              <dd className="mt-0.5 text-sm font-semibold">{formatDateTime(s.submittedAt)}</dd>
            </div>
            <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 backdrop-blur-md">
              <dt className="text-[11px] text-on-ink/60">{s.reviewedAt ? "Reviewed" : "Documents"}</dt>
              <dd className="mt-0.5 text-sm font-semibold">{s.reviewedAt ? formatDateTime(s.reviewedAt) : `${s.documents.length} uploaded`}</dd>
            </div>
          </dl>
        </div>
      </section>

      {nameMismatch && (
        <div role="status" className="flex items-start gap-3 rounded-2xl border border-amber-300/70 bg-amber-50/70 p-4 text-sm dark:border-amber-500/30 dark:bg-amber-500/[0.07]">
          <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
            <TriangleAlert className="size-4" />
          </span>
          <p>
            <span className="block font-semibold text-amber-800 dark:text-amber-300">The legal name differs from the account name</span>
            <span className="text-muted">
              Documents say <strong className="text-foreground">{s.legalFirstName} {s.legalLastName}</strong>; the account is{" "}
              <strong className="text-foreground">{account.firstName} {account.lastName}</strong>. Check the document carefully.
            </span>
          </p>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="min-w-0 space-y-6 lg:col-span-2">
          <Card title="Submitted details">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              {s.aadhaarNumber && (
                <DetailTile icon={Fingerprint} label="Aadhaar number">
                  <span className="font-mono text-base font-bold tracking-wide">{formatAadhaar(s.aadhaarNumber)}</span>
                </DetailTile>
              )}
              {s.panNumber && (
                <DetailTile icon={CreditCard} label="PAN"><span className="font-mono text-base font-bold tracking-wide">{s.panNumber}</span></DetailTile>
              )}
              {s.documentType && (
                <>
                  <DetailTile icon={IdCard} label="Document"><span className="font-mono text-base font-bold tracking-wide">{kycNumbers(s)}</span></DetailTile>
                  <DetailTile icon={Globe} label="Issued by">{s.documentIssuingCountry}</DetailTile>
                  <DetailTile icon={CalendarDays} label="Expires">{formatDate(s.documentExpiryDate)}</DetailTile>
                </>
              )}
              <DetailTile icon={CalendarDays} label="Date of birth">{formatDayMonthYear(s.dateOfBirth)}</DetailTile>
              <DetailTile icon={Flag} label="Nationality">{COUNTRY_OPTIONS.find((c) => c.value === s.nationality)?.label ?? s.nationality}</DetailTile>
              <DetailTile icon={MapPin} label="Address (from profile)" className="sm:col-span-2">
                {address?.line1 ? <AddressLines address={address} /> : <span className="text-muted">Not given</span>}
              </DetailTile>
              <DetailTile icon={Clock} label="Submitted">{formatDateTime(s.submittedAt)}</DetailTile>
              {s.status !== "PENDING" && s.assignee && <DetailTile icon={UserCheck} label="Reviewed by">{s.assignee.name}</DetailTile>}
              {s.reviewedAt && <DetailTile icon={BadgeCheck} label="Reviewed">{formatDateTime(s.reviewedAt)}</DetailTile>}
              {s.rejectionReason && (
                <DetailTile icon={MessageSquareWarning} label="Rejection reason" className="sm:col-span-2">{s.rejectionReason}</DetailTile>
              )}
            </dl>
          </Card>

          <Card title="Evidence" description="Each document you open is recorded in the audit log.">
            {s.documents.length === 0 ? (
              <p className="text-sm text-muted">No documents uploaded.</p>
            ) : (
              <ul className="grid gap-3 sm:grid-cols-2">
                {s.documents.map((doc) => (
                  <li key={doc.id}>
                    <a href={`/admin/kyc/${s.id}/documents/${doc.id}`} target="_blank" rel="noopener noreferrer"
                      className="group flex items-center gap-3 rounded-xl border border-border bg-background/60 p-3 transition duration-200 hover:-translate-y-0.5 hover:border-gold/50 hover:shadow-md">
                      <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text transition-colors group-hover:bg-gold group-hover:text-on-gold">
                        <FileText className="size-5" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block font-medium">{KYC_DOCUMENT_LABEL[doc.purpose]}</span>
                        <span className="block text-xs text-muted">Opens in a new tab</span>
                      </span>
                      <ExternalLink aria-hidden="true" className="size-4 shrink-0 text-muted transition-colors group-hover:text-gold-text" />
                    </a>
                  </li>
                ))}
              </ul>
            )}
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
                  {banks.map((bank, i) => (
                    <div key={bank.account.id} className={i > 0 ? "border-t border-border pt-6" : undefined}>
                      <BankAccountReview item={bank} viewerId={session.userId} canVerify={identityApproved} sharedAssignment />
                    </div>
                  ))}
                </div>
              )}
            </Card>
          )}
        </div>

        {ownSubmission && s.status === "PENDING" ? (
          <Card title="Decision" className="lg:sticky lg:top-6 lg:self-start">
            <Notice tone="warning">You cannot review your own verification.</Notice>
          </Card>
        ) : assignees.length > 0 ? (
          <Card title="Decision" className="lg:sticky lg:top-6 lg:self-start">
            <div className="space-y-4">
              <p className="flex items-start gap-2 text-xs text-muted">
                <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-gold-text" />
                Check the numbers against the documents and the name against the account before deciding.
              </p>
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
            </div>
          </Card>
        ) : (s.status === "APPROVED" || s.status === "REJECTED") && (
          <aside className={`flex items-start gap-3 rounded-2xl border p-5 text-sm shadow-sm lg:sticky lg:top-6 lg:self-start ${identityApproved
            ? "border-emerald-200 bg-emerald-50/70 dark:border-emerald-500/30 dark:bg-emerald-500/[0.07]"
            : "border-border bg-surface"}`}>
            <span aria-hidden="true" className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${identityApproved
              ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300" : "bg-background text-muted ring-1 ring-border"}`}>
              {identityApproved ? <BadgeCheck className="size-4" /> : <Clock className="size-4" />}
            </span>
            <p>
              <span className="block font-semibold">{identityApproved ? "Decided: approved" : "Decided: rejected"}</span>
              <span className="text-muted">Nothing left to decide here.</span>
            </p>
          </aside>
        )}
      </div>
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
