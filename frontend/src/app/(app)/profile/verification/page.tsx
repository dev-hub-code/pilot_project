import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { KycDocumentForm } from "@/features/profile/kyc-form";
import { authFetch } from "@/lib/server/auth/session";
import type { KycSubmission, Profile } from "@/types/user";
import { formatAadhaar, kycNumbers, missingPersonalDetails } from "@/features/profile/kyc-labels";
import { formatDateTime, formatDayMonthYear, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Identity verification" };

export default async function VerificationPage({ searchParams }: PageProps<"/profile/verification">) {
  const fromPersonal = (await searchParams).step === "personal";
  // The backend answers 204 (no body) when nothing has been uploaded yet.
  const [submitted, profile] = await Promise.all([
    authFetch<KycSubmission | undefined>("/api/v1/users/me/kyc"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  const latest = submitted ?? null;
  // A draft holds whichever of Aadhaar and PAN is uploaded so far; nothing is reviewed until both are in.
  const draft = latest?.status === "DRAFT" ? latest : null;
  const reviewed = latest && latest.status !== "DRAFT" ? latest : null;
  const canUpload = !latest || latest.status === "REJECTED" || draft !== null;
  const missing = missingPersonalDetails(profile);

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        {fromPersonal && (
          <Notice tone="success">Personal details saved. Next, verify your identity with your Aadhaar and PAN.</Notice>
        )}
        {reviewed && (
          <Card title="Latest submission" actions={<StatusBadge tone={toneFor(reviewed.status)}>{humanize(reviewed.status)}</StatusBadge>}>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Submitted">{formatDateTime(reviewed.submittedAt)}</Item>
              <Item label="Legal name">{reviewed.legalFirstName} {reviewed.legalLastName}</Item>
              <Item label="Documents">{kycNumbers(reviewed)}</Item>
            </dl>
            <div className="mt-4">
              {reviewed.status === "PENDING" && (
                <Notice>We are reviewing your Aadhaar and PAN. This usually takes one to two business days.</Notice>
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
            description="Required by regulation before you can invest or withdraw. Upload your Aadhaar and PAN together or one at a time: we review them once both are in.">
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
                {draft && (
                  <Notice>
                    {draft.aadhaarNumber ? "Your Aadhaar is uploaded. Upload your PAN" : "Your PAN is uploaded. Upload your Aadhaar"}{" "}
                    to send them for review.
                  </Notice>
                )}
                <div className="grid gap-px bg-border md:grid-cols-2">
                  <DocumentSlot title="Aadhaar card" uploaded={draft?.aadhaarNumber ? `Aadhaar ${formatAadhaar(draft.aadhaarNumber)}` : null}>
                    <KycDocumentForm document="AADHAAR" replacing={Boolean(draft?.aadhaarNumber)} />
                  </DocumentSlot>
                  <DocumentSlot title="PAN card" uploaded={draft?.panNumber ? `PAN ${draft.panNumber}` : null}>
                    <KycDocumentForm document="PAN" replacing={Boolean(draft?.panNumber)} />
                  </DocumentSlot>
                </div>
                <p className="text-xs text-muted">
                  Your Aadhaar and PAN numbers and documents are stored encrypted and only visible to our verification
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
          <li>Your 12-digit Aadhaar number and your Aadhaar card as one file showing both sides, e.g. your e-Aadhaar PDF</li>
          <li>Your PAN, e.g. ABCDE1234F, and your PAN card</li>
          <li>Files as PDF, JPEG or PNG, up to 5 MB each, clear and readable</li>
          <li>You can upload them at different times; we review them once both are in</li>
        </ul>
      </Card>
    </div>
  );
}

/** One document: uploaded (and replaceable while the other is still to come) or still to upload. */
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
