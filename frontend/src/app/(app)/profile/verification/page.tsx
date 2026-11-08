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
import {
  BadgeCheck, CalendarDays, CheckCircle2, Clock, CreditCard, FileCheck2, Fingerprint, Flag, Hourglass, IdCard, ImageUp, type LucideIcon,
  PenLine, ShieldCheck, UserRound, XCircle,
} from "lucide-react";

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
  const uploadedCount = 3 - stillNeeded.length;
  const reviewStyle = reviewed ? STATUS_STYLE[reviewed.status === "APPROVED" || reviewed.status === "REJECTED" ? reviewed.status : "PENDING"] : null;

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        {fromPersonal && canUpload && (
          <Notice tone="success">Personal details saved. Next, verify your identity with your Aadhaar, PAN and signature.</Notice>
        )}
        {reviewed && (
          <section aria-labelledby="kyc-status" className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
            <div className={`flex flex-wrap items-center gap-4 border-b p-5 sm:p-6 ${reviewStyle?.band}`}>
              {reviewStyle && (
                <span aria-hidden="true" className={`flex size-12 shrink-0 items-center justify-center rounded-2xl ${reviewStyle.tile}`}>
                  <reviewStyle.icon className="size-6" />
                </span>
              )}
              <div className="min-w-0 flex-1">
                <h2 id="kyc-status" className="text-lg font-semibold tracking-tight">
                  {reviewed.status === "APPROVED" ? "Your identity is verified" : reviewed.status === "REJECTED" ? "Not approved" : "Under review"}
                </h2>
                <p className="text-sm text-muted">
                  {reviewed.status === "PENDING" && "We are reviewing your Aadhaar, PAN and signature. This usually takes one to two business days."}
                  {reviewed.status === "APPROVED" && (reviewed.reviewedAt ? `Verified on ${formatDateTime(reviewed.reviewedAt)}.` : "You can invest and withdraw.")}
                  {reviewed.status === "REJECTED" && <>{reviewed.rejectionReason}. Please upload your documents again with the issue corrected.</>}
                </p>
              </div>
              <StatusBadge tone={toneFor(reviewed.status)}>{reviewed.status === "APPROVED" ? "Verified" : humanize(reviewed.status)}</StatusBadge>
            </div>
            <dl className="grid gap-3 p-5 text-sm sm:grid-cols-2 sm:p-6">
              <Item icon={UserRound} label="Full name">{reviewed.legalFirstName} {reviewed.legalLastName}</Item>
              <Item icon={CalendarDays} label="Date of birth">{formatDayMonthYear(reviewed.dateOfBirth)}</Item>
              <Item icon={Flag} label="Nationality">
                {COUNTRY_OPTIONS.find((c) => c.value === reviewed.nationality)?.label ?? reviewed.nationality}
              </Item>
              {reviewed.aadhaarNumber || reviewed.panNumber ? (
                <>
                  <Item icon={Fingerprint} label="Aadhaar number">
                    <span className="font-mono text-base font-bold tracking-wide">
                      {reviewed.aadhaarNumber ? formatAadhaar(reviewed.aadhaarNumber) : "—"}
                    </span>
                  </Item>
                  <Item icon={CreditCard} label="PAN">
                    <span className="font-mono text-base font-bold tracking-wide">{reviewed.panNumber ?? "—"}</span>
                  </Item>
                </>
              ) : (
                <Item icon={IdCard} label="Identity document">{kycNumbers(reviewed)}</Item>
              )}
              <Item icon={FileCheck2} label="Documents submitted">
                {reviewed.documents.length > 0
                  ? reviewed.documents.map((d) => KYC_DOCUMENT_LABEL[d.purpose]).join(", ")
                  : "—"}
              </Item>
              <Item icon={Clock} label="Submitted on">{formatDateTime(reviewed.submittedAt)}</Item>
              {reviewed.reviewedAt && (
                <Item icon={BadgeCheck} label={reviewed.status === "APPROVED" ? "Verified on" : "Reviewed on"}>{formatDateTime(reviewed.reviewedAt)}</Item>
              )}
            </dl>
          </section>
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
                <div className="flex flex-wrap items-center gap-3 rounded-xl bg-background/60 p-3 ring-1 ring-border">
                  <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-gold/10 text-xs font-semibold text-gold-text">
                    {(profile.firstName.charAt(0) + profile.lastName.charAt(0)).toUpperCase() || "?"}
                  </span>
                  <p className="min-w-0 flex-1 text-sm">
                    <span className="block font-medium">{profile.firstName} {profile.lastName}</span>
                    <span className="block text-xs text-muted">
                      Born {formatDayMonthYear(profile.dateOfBirth)} · Nationality {profile.nationality} · taken from your profile
                    </span>
                  </p>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="font-medium">{uploadedCount} of 3 uploaded</span>
                    <span className="text-xs text-muted">
                      {stillNeeded.length === 0 ? "Ready for review" : `Still needed: ${stillNeeded.join(", ")}`}
                    </span>
                  </div>
                  <div className="grid grid-cols-3 gap-1.5" aria-hidden="true">
                    {[0, 1, 2].map((i) => (
                      <span key={i} className={`h-1.5 rounded-full transition-colors ${i < uploadedCount ? "bg-gold" : "bg-border"}`} />
                    ))}
                  </div>
                </div>

                <ol className="space-y-3">
                  <DocumentSlot step={1} icon={Fingerprint} title="Aadhaar card" hint="One image showing both sides"
                    uploaded={draft?.aadhaarNumber ? `Aadhaar ${formatAadhaar(draft.aadhaarNumber)}` : null}>
                    <KycDocumentForm document="AADHAAR" replacing={Boolean(draft?.aadhaarNumber)} />
                  </DocumentSlot>
                  <DocumentSlot step={2} icon={CreditCard} title="PAN card" hint="Your PAN, e.g. ABCDE1234F, and the card"
                    uploaded={draft?.panNumber ? `PAN ${draft.panNumber}` : null}>
                    <KycDocumentForm document="PAN" replacing={Boolean(draft?.panNumber)} />
                  </DocumentSlot>
                  <DocumentSlot step={3} icon={PenLine} title="Signature" hint="Signed on plain white paper"
                    uploaded={hasSignature ? "Signature image" : null}>
                    <KycDocumentForm document="SIGNATURE" replacing={hasSignature} />
                  </DocumentSlot>
                </ol>
              </div>
            )}
          </Card>
        )}
      </div>
      <aside className="space-y-4 lg:sticky lg:top-6 lg:self-start">
        <section aria-labelledby="need-title" className="rounded-2xl border border-border bg-surface p-5 shadow-sm sm:p-6">
          <h2 id="need-title" className="flex items-center gap-2 font-semibold tracking-tight">
            <span aria-hidden="true" className="h-4 w-1 rounded-full bg-gold" /> What you need
          </h2>
          <ul className="mt-4 space-y-3 text-sm">
            {NEEDS.map((n) => (
              <li key={n.title} className="flex gap-3">
                <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text">
                  <n.icon className="size-4" />
                </span>
                <span className="min-w-0">
                  <span className="block font-medium">{n.title}</span>
                  <span className="block text-xs leading-relaxed text-muted">{n.text}</span>
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-4 border-t border-border pt-4 text-xs text-muted">
            Upload them together or at different times: we review them once all three are in. Name, date of birth and
            nationality come from your <Link href="/profile" className="font-medium text-gold-text hover:underline">profile</Link>.
          </p>
        </section>
        <section className="relative isolate overflow-hidden rounded-2xl bg-ink p-5 text-on-ink shadow-lg shadow-ink/15 sm:p-6">
          <div aria-hidden="true" className="absolute -top-16 -right-16 -z-10 size-44 rounded-full bg-gold/30 blur-3xl" />
          <ShieldCheck aria-hidden="true" className="size-6 text-gold-on-ink" />
          <h2 className="mt-3 font-semibold">Kept private</h2>
          <p className="mt-1 text-sm text-on-ink/70">
            Your Aadhaar and PAN numbers, documents and signature are stored encrypted and only visible to our verification
            team. Every access is logged.
          </p>
        </section>
      </aside>
    </div>
  );
}

const STATUS_STYLE: Record<"APPROVED" | "PENDING" | "REJECTED", { icon: LucideIcon; tile: string; band: string }> = {
  APPROVED: {
    icon: CheckCircle2,
    tile: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300",
    band: "border-emerald-200 bg-emerald-50/70 dark:border-emerald-500/20 dark:bg-emerald-500/[0.07]",
  },
  PENDING: {
    icon: Hourglass,
    tile: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
    band: "border-amber-200 bg-amber-50/70 dark:border-amber-500/20 dark:bg-amber-500/[0.07]",
  },
  REJECTED: {
    icon: XCircle,
    tile: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300",
    band: "border-rose-200 bg-rose-50/70 dark:border-rose-500/20 dark:bg-rose-500/[0.07]",
  },
};

const NEEDS: { icon: LucideIcon; title: string; text: string }[] = [
  { icon: UserRound, title: "Personal details", text: "Mobile phone, date of birth, nationality and address, completed in your profile." },
  { icon: Fingerprint, title: "Aadhaar", text: "Your 12-digit number and the card as one image showing both sides." },
  { icon: CreditCard, title: "PAN", text: "Your PAN, e.g. ABCDE1234F, and your PAN card." },
  { icon: PenLine, title: "Signature", text: "Signed on plain white paper, photographed or scanned." },
  { icon: ImageUp, title: "Clear images", text: "Each file a JPG or PNG, up to 2 MB, clear and readable." },
];

/** One document: uploaded (and replaceable while the others are still to come) or still to upload. */
function DocumentSlot({ step, icon: Icon, title, hint, uploaded, children }: {
  step: number;
  icon: LucideIcon;
  title: string;
  hint: string;
  uploaded: string | null;
  children: React.ReactNode;
}) {
  return (
    <li aria-label={title}
      className={`space-y-4 rounded-2xl border p-4 transition-colors sm:p-5 ${uploaded
        ? "border-emerald-200 bg-emerald-50/50 dark:border-emerald-500/25 dark:bg-emerald-500/[0.05]"
        : "border-border bg-surface hover:border-gold/40"}`}>
      <div className="flex items-center gap-3">
        <span aria-hidden="true" className={`relative flex size-11 shrink-0 items-center justify-center rounded-xl ${uploaded
          ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300" : "bg-gold/10 text-gold-text"}`}>
          <Icon className="size-5" />
          <span className={`absolute -top-1.5 -right-1.5 flex size-5 items-center justify-center rounded-full text-[10px] font-bold ring-2 ring-surface ${uploaded
            ? "bg-emerald-600 text-white" : "bg-ink text-on-ink dark:bg-gold dark:text-on-gold"}`}>
            {uploaded ? <CheckCircle2 className="size-3" /> : step}
          </span>
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold">{title}</h3>
          <p className="truncate text-xs text-muted">{uploaded ?? hint}</p>
        </div>
        <StatusBadge tone={uploaded ? "success" : "neutral"}>{uploaded ? "Uploaded" : "Needed"}</StatusBadge>
      </div>
      {uploaded ? (
        <details className="text-sm">
          <summary className="inline-flex cursor-pointer list-none items-center gap-1.5 rounded-lg border border-border bg-surface px-3 py-1.5 text-xs font-medium text-muted transition-colors hover:border-gold hover:text-gold-text">
            <ImageUp aria-hidden="true" className="size-3.5" /> Upload a different one
          </summary>
          <div className="mt-4">{children}</div>
        </details>
      ) : children}
    </li>
  );
}

function Item({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 gap-3 rounded-xl bg-background/60 p-3">
      <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface text-gold-text shadow-sm">
        <Icon className="size-4" />
      </span>
      <div className="min-w-0">
        <dt className="text-xs text-muted">{label}</dt>
        <dd className="mt-0.5 font-medium break-words">{children}</dd>
      </div>
    </div>
  );
}
