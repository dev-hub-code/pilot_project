import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { KycForm } from "@/features/profile/kyc-form";
import { authFetch } from "@/lib/server/auth/session";
import type { KycSubmission } from "@/types/user";
import { formatDate, formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Identity verification" };

export default async function VerificationPage() {
  // The backend answers 204 (no body) when nothing has been submitted yet.
  const latest = (await authFetch<KycSubmission | undefined>("/api/v1/users/me/kyc")) ?? null;
  const canSubmit = !latest || latest.status === "REJECTED";

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        {latest && (
          <Card title="Latest submission" actions={<StatusBadge tone={toneFor(latest.status)}>{humanize(latest.status)}</StatusBadge>}>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <Item label="Submitted">{formatDateTime(latest.submittedAt)}</Item>
              <Item label="Document">{humanize(latest.documentType)} {latest.documentNumberMasked}</Item>
              <Item label="Legal name">{latest.legalFirstName} {latest.legalLastName}</Item>
              <Item label="Expires">{formatDate(latest.documentExpiryDate)}</Item>
            </dl>
            <div className="mt-4">
              {latest.status === "PENDING" && (
                <Notice>We are reviewing your documents. This usually takes one to two business days.</Notice>
              )}
              {latest.status === "APPROVED" && <Notice tone="success">Your identity is verified.</Notice>}
              {latest.status === "REJECTED" && (
                <Notice tone="warning">
                  Not approved: {latest.rejectionReason}. Please submit again with the issue corrected.
                </Notice>
              )}
            </div>
          </Card>
        )}
        {canSubmit && (
          <Card title={latest ? "Submit again" : "Verify your identity"}
            description="Required by regulation before you can invest or withdraw.">
            <KycForm />
          </Card>
        )}
      </div>
      <Card title="What you need">
        <ul className="list-disc space-y-2 pl-5 text-sm text-muted">
          <li>A valid passport, national ID card or driving licence</li>
          <li>Clear photos of the document (front and back for cards)</li>
          <li>A selfie holding the document next to your face</li>
          <li>Optionally, a recent proof of address</li>
        </ul>
      </Card>
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
