import { BellRing, FileText, LifeBuoy, LockKeyhole, ReceiptText, ShieldCheck } from "lucide-react";
import { FeatureCard, Section, SectionHeader } from "./landing-ui";

/** What investors get from SeaLease beyond the plan terms. Each point is said here only. */
const REASONS = [
  {
    icon: <FileText className="size-5" />, title: "An agreement for every investment",
    body: "Each investment has its own agreement in your name, ready to read before you accept it and to download any time.",
  },
  {
    icon: <ReceiptText className="size-5" />, title: "Every rupee on record",
    body: "Each credit is listed under Earnings, every order has an invoice, and you can download a statement of your withdrawals as PDF or CSV.",
  },
  {
    icon: <ShieldCheck className="size-5" />, title: "Paid only to verified accounts",
    body: "Bank withdrawals go only to accounts our team has verified, so your money cannot be sent anywhere you did not add.",
  },
  {
    icon: <LockKeyhole className="size-5" />, title: "Your details kept safe",
    body: "Identity documents, tax IDs and bank account numbers are stored encrypted. Only authorised staff can view them, and every view of a KYC document is logged.",
  },
  {
    icon: <BellRing className="size-5" />, title: "Notified at every step",
    body: "You hear from us when your KYC is approved, your bank account is verified, your agreement is ready and your withdrawal moves.",
  },
  {
    icon: <LifeBuoy className="size-5" />, title: "People who can help",
    body: "Raise a support ticket from your account and our team answers it there, with the whole conversation kept on record.",
  },
];

export function WhySeaLease() {
  return (
    <Section id="why" labelledBy="why-heading">
      <SectionHeader id="why-heading" pill="Why SeaLease" lead="Built To Be" soft="Clear And Accountable" center>
        The plan sets your returns. Everything around it is designed so you always know where your money is.
      </SectionHeader>
      <ul className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {REASONS.map((reason) => (
          <FeatureCard key={reason.title} icon={reason.icon} title={reason.title}>{reason.body}</FeatureCard>
        ))}
      </ul>
    </Section>
  );
}
