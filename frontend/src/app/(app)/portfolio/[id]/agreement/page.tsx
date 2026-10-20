import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { AcceptAgreementForm } from "@/features/agreement/accept-form";
import { AgreementText } from "@/features/agreement/agreement-text";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { getRentCredit } from "@/lib/server/rent-credit";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { LeaseAgreementDocument } from "@/types/agreement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Lease agreement" };

export default async function AgreementPage({ params, searchParams }: PageProps<"/portfolio/[id]/agreement">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const justAccepted = (await searchParams).accepted === "1";
  const credit = await getRentCredit();
  let doc: LeaseAgreementDocument;
  try {
    doc = await authFetch<LeaseAgreementDocument>(`/api/v1/agreements/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { agreement } = doc;
  const awaiting = agreement.acceptanceRequired && !agreement.acceptedAt;

  return (
    <div className="space-y-6">
      <PageHeader title="Lease agreement"
        description={`${agreement.agreementNumber} · container ${agreement.containerNumber} · issued ${formatDateTime(agreement.generatedAt)}`} />
      {justAccepted && agreement.acceptedAt && (
        <Notice tone="success">Accepted. Your lease starts at the next full hour, and rent is credited to your wallet {credit.label} from then on.</Notice>
      )}
      {awaiting ? (
        <Notice tone="warning">Read the agreement below. Your lease and rent start only once you accept it.</Notice>
      ) : agreement.acceptedAt && !justAccepted && (
        <Notice tone="info">You accepted this agreement on {formatDateTime(agreement.acceptedAt)}.</Notice>
      )}

      <AgreementText title={agreement.title} body={doc.body} />

      <div className="flex flex-wrap items-center gap-4 text-sm">
        <a href={`/portfolio/agreements/${id}`} className="text-gold-text hover:underline">Download PDF</a>
        <Link href="/portfolio" className="text-muted hover:underline">Back to portfolio</Link>
      </div>

      {awaiting && (
        <Card title="Accept the agreement">
          <AcceptAgreementForm holdingId={id} agreementNumber={agreement.agreementNumber} />
        </Card>
      )}
    </div>
  );
}
