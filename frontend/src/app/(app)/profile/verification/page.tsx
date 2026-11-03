import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { KycDocumentForm } from "@/features/profile/kyc-form";
import { authFetch } from "@/lib/server/auth/session";
import { requireEmailVerified } from "@/lib/server/onboarding";
import type { KycSubmission, Profile } from "@/types/user";
import { formatAadhaar, KYC_DOCUMENT_LABEL, kycNumbers, missingPersonalDetails } from "@/features/profile/kyc-labels";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import { formatDateTime, formatDayMonthYear, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Identity verification" };

export default async function VerificationPage({ searchParams }: PageProps<"/profile/verification">) {
  await requireEmailVerified();
  const fromPersonal = (await searchParams).step === "personal";
  // The backend answers 204 (no body) when nothing has been uploaded yet.
  const [submitted, profile] = await Promise.all([
    authFetch<KycSubmission | undefined>("/api/v1/users/me/kyc"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  const latest = submitted ?? null;
  // A draft holds whichever of Aadhaar, PAN and signature is uploaded so far; nothing is reviewed until all three are in.
  const draft = latest?.status === "DRAFT" ? latest : null;
  const reviewed = latest && latest.status !== "DRAFT" ? latest : null;
  const canUpload = !latest || latest.status === "REJECTED" || draft !== null;
  const hasSignature = Boolean(draft?.documents.some((d) => d.purpose === "KYC_SIGNATURE"));
  const stillNeeded = [
    !draft?.aadhaarNumber && "Aadhaar",
    !draft?.panNumber && "PAN",
    !hasSignature && "signature",
  ].filter((item): item is string => Boolean(item));
  const missing = missingPersonalDetails(profile);

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        {fromPersonal && (
          <Notice tone="success">Personal details saved. Next, verify your identity with your Aadhaar, PAN and signature.</Notice>
        )}
        {reviewed && (
          <Card title={reviewed.status === "APPROVED" ? "Verified identity" : "Latest submission"}
            actions={<StatusBadge tone={toneFor(reviewed.status)}>{reviewed.status === "APPROVED" ? "Verified" : humanize(reviewed.status)}</StatusBadge>}>
            <dl className="grid gap-x-6 gap-y-4 text-sm sm:grid-cols-2">
              <Item label="Full name">{reviewed.legalFirstName} {reviewed.legalLastName}</Item>
              <Item label="Date of birth">{formatDayMonthYear(reviewed.dateOfBirth)}</Item>
              <Item label="Nationality">
                {COUNTRY_OPTIONS.find((c) => c.value === reviewed.nationality)?.label ?? reviewed.nationality}
              </Item>
              {reviewed.aadhaarNumber || reviewed.panNumber ? (
                <>
                  <Item label="Aadhaar number">
                    <span className="font-mono text-base font-bold tracking-wide">
                      {reviewed.aadhaarNumber ? formatAadhaar(reviewed.aadhaarNumber) : "—"}
                    </span>
                  </Item>
                  <Item label="PAN">
                    <span className="font-mono text-base font-bold tracking-wide">{reviewed.panNumber ?? "—"}</span>
                  </Item>
                </>
              ) : (
                <Item label="Identity document">{kycNumbers(reviewed)}</Item>
              )}
              <Item label="Documents submitted">
                {reviewed.documents.length > 0
                  ? reviewed.documents.map((d) => KYC_DOCUMENT_LABEL[d.purpose]).join(", ")
                  : "—"}
              </Item>
              <Item label="Submitted on">{formatDateTime(reviewed.submittedAt)}</Item>
              {reviewed.reviewedAt && (
                <Item label={reviewed.status === "APPROVED" ? "Verified on" : "Reviewed on"}>{formatDateTime(reviewed.reviewedAt)}</Item>
              )}
            </dl>
            <div className="mt-4">
              {reviewed.status === "PENDING" && (
                <Notice>We are reviewing your Aadhaar, PAN and signature. This usually takes one to two business days.</Notice>
              )}
              {reviewed.status === "APPROVED" && <Notice tone="success">Your identity is verified.</Notice>}
              {reviewed.status === "REJECTED" && (
                <Notice tone="warning">
                  Not approved: {reviewed.rejectionReason}. Please upload your documents again with the issue corrected.
                </Notice>
              )}
            </div>
          </Card>
        )}
        {canUpload && (
          <Card title={reviewed ? "Upload again" : "Verify your identity"}
            description="Required by regulation before you can invest or withdraw. Upload your Aadhaar, PAN and signature together or one at a time: we review them once all three are in.">
            {missing.length > 0 ? (
              <div className="space-y-4">
                <Notice tone="warning">
                  First complete your personal details. Still needed: {missing.join(", ")}.
                </Notice>
                <LinkButton href="/profile">Complete personal details</LinkButton>
              </div>
            ) : (
              <div className="space-y-6">
                <p className="text-sm text-muted">
                  Submitted with your profile details: {profile.firstName} {profile.lastName}, born{" "}
                  {formatDayMonthYear(profile.dateOfBirth)}, nationality {profile.nationality}.
                </p>
                {draft && stillNeeded.length > 0 && (
                  <Notice>Upload your {stillNeeded.join(" and ")} to send your documents for review.</Notice>
                )}
                <div className="grid gap-px overflow-hidden rounded-xl border border-border bg-border">
                  <DocumentSlot title="Aadhaar card" uploaded={draft?.aadhaarNumber ? `Aadhaar ${formatAadhaar(draft.aadhaarNumber)}` : null}>
                    <KycDocumentForm document="AADHAAR" replacing={Boolean(draft?.aadhaarNumber)} />
                  </DocumentSlot>
                  <DocumentSlot title="PAN card" uploaded={draft?.panNumber ? `PAN ${draft.panNumber}` : null}>
                    <KycDocumentForm document="PAN" replacing={Boolean(draft?.panNumber)} />
                  </DocumentSlot>
                  <DocumentSlot title="Signature" uploaded={hasSignature ? "Signature image" : null}>
                    <KycDocumentForm document="SIGNATURE" replacing={hasSignature} />
                  </DocumentSlot>
                </div>
                <p className="text-xs text-muted">
                  Your Aadhaar and PAN numbers, documents and signature are stored encrypted and only visible to our verification
                  team. Every access is logged.
                </p>
              </div>
            )}
          </Card>
        )}
      </div>
      <Card title="What you need">
        <ul className="list-disc space-y-2 pl-5 text-sm text-muted">
          <li>
            Your personal details completed in your <Link href="/profile" className="underline">profile</Link>: mobile
            phone, date of birth, nationality and address. Your name, date of birth and nationality are taken from there.
          </li>
          <li>Your 12-digit Aadhaar number and your Aadhaar card as one image showing both sides</li>
          <li>Your PAN, e.g. ABCDE1234F, and your PAN card</li>
          <li>Your signature, signed on plain white paper and photographed or scanned</li>
          <li>Each file a JPG or PNG image, up to 2 MB, clear and readable</li>
          <li>You can upload them at different times; we review them once all three are in</li>
        </ul>
      </Card>
    </div>
  );
}

/** One document: uploaded (and replaceable while the others are still to come) or still to upload. */
function DocumentSlot({ title, uploaded, children }: { title: string; uploaded: string | null; children: React.ReactNode }) {
  return (
    <section className="space-y-4 bg-surface p-5" aria-label={title}>
      <div className="flex items-center justify-between gap-3">
        <h3 className="font-semibold">{title}</h3>
        <StatusBadge tone={uploaded ? "success" : "neutral"}>{uploaded ? "Uploaded" : "Needed"}</StatusBadge>
      </div>
      {uploaded ? (
        <>
          <p className="text-sm">{uploaded}</p>
          <details className="text-sm">
            <summary className="cursor-pointer text-muted">Upload a different one</summary>
            <div className="mt-4">{children}</div>
          </details>
        </>
      ) : children}
    </section>
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
