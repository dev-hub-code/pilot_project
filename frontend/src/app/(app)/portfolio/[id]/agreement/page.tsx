import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { AcceptAgreementForm } from "@/features/agreement/accept-form";
import { AgreementText } from "@/features/agreement/agreement-text";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { LeaseAgreementDocument } from "@/types/agreement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Investment agreement" };

export default async function AgreementPage({ params }: PageProps<"/portfolio/[id]/agreement">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  let doc: LeaseAgreementDocument;
  try {
    doc = await authFetch<LeaseAgreementDocument>(`/api/v1/agreements/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { agreement } = doc;
  // Only an agreement still to accept opens here; once accepted it is downloaded from the portfolio as a PDF.
  if (!agreement.acceptanceRequired || agreement.acceptedAt) redirect("/portfolio");

  return (
    <div className="space-y-6">
      <PageHeader title="Investment agreement"
        description={`${agreement.agreementNumber} · issued ${formatDateTime(agreement.generatedAt)}`} />
      <Notice tone="warning">Read the agreement below. Your term and returns start only once you accept it.</Notice>

      <AgreementText title={agreement.title} body={doc.body} />

      <div className="flex flex-wrap items-center gap-4 text-sm">
        <a href={`/portfolio/agreements/${id}`} className="inline-flex min-h-6 items-center text-gold-text hover:underline">Download PDF</a>
      </div>

      <Card title="Accept the agreement">
        <AcceptAgreementForm holdingId={id} agreementNumber={agreement.agreementNumber} />
      </Card>
    </div>
  );
}
