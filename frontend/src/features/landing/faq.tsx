import { Heading, Pill, Section, glass } from "./landing-ui";

/**
 * Detailed questions the rest of the page does not answer. What SeaLease is, how returns are worked out, the plans,
 * the steps and the account figures each have their own section: do not repeat them here. Every answer must match
 * how the platform works today.
 */
const FAQS = [
  {
    question: "How do I pay for an investment?",
    answer: "By bank payment to one of our company bank accounts, online (NEFT, RTGS, IMPS or UPI) or by cheque, quoting your payment reference and entering the transaction ID or cheque number; or in cash at our office. Our finance team confirms the payment. Card payments are not accepted.",
  },
  {
    question: "How and when is my money paid out?",
    answer: "Online investors are paid by bank transfer into their verified primary bank account and can request a withdrawal at any time. Network investors choose bank or cash at our office, can change that at any time, and are paid when our team makes a withdrawal. Small balances wait for the next withdrawal.",
  },
  {
    question: "Can I hold more than one investment?",
    answer: "Yes. You can invest in any plan open to you, as often as you like. Each investment has its own term, its own agreement and its own returns, all shown separately in your portfolio.",
  },
  {
    question: "What happens at the end of the term?",
    answer: "When the last returns of the term are credited, your investment is marked as completed and its returns stop. Anything left in your wallet stays yours to withdraw, and you can invest in a new plan.",
  },
  {
    question: "Can I earn by referring others?",
    answer: "Yes. Share your referral link from the Referrals page. When someone you invite makes their first investment, you earn a one-time commission on it. SeaLease pays it, so their own returns are never reduced. The current rate is shown on your Referrals page.",
  },
];

export function Faq() {
  return (
    <Section id="faq" labelledBy="faq-heading" className="grid gap-10 lg:grid-cols-[1fr_1.6fr] lg:gap-14">
      <div className="space-y-5 lg:sticky lg:top-28 lg:self-start">
        <Pill>FAQ</Pill>
        <Heading id="faq-heading" lead="Questions" soft="Investors Ask Us" />
        <p className="max-w-sm text-sm leading-relaxed text-muted">
          Can&apos;t find your answer?{" "}
          <a href="#contact" className="font-medium text-gold-text underline-offset-4 hover:underline">Talk to us</a> and
          an investment specialist will get back to you.
        </p>
      </div>
      <div className="space-y-3">
        {FAQS.map((faq) => (
          <details key={faq.question}
            className={`group rounded-2xl px-6 py-5 transition duration-300 open:border-brass/50 open:bg-glass-strong ${glass}`}>
            <summary className="flex cursor-pointer list-none items-center justify-between gap-6 rounded-lg text-base font-semibold focus-visible:ring-2 focus-visible:ring-brass focus-visible:ring-offset-4 focus-visible:outline-none [&::-webkit-details-marker]:hidden">
              {faq.question}
              <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-ink text-lg leading-none text-on-ink transition duration-300 group-open:rotate-45 dark:bg-on-ink dark:text-ink">+</span>
            </summary>
            <p className="mt-3 pr-10 text-sm leading-relaxed text-muted">{faq.answer}</p>
          </details>
        ))}
      </div>
    </Section>
  );
}
