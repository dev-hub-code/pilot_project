import { FileSignature, Landmark, Layers, UserRoundCheck } from "lucide-react";
import { CtaLink, Section, SectionHeader, glass } from "./landing-ui";

/** The one place the investment process is explained, from sign-up to money in the bank. */
export function HowItWorks({ credit }: { credit: string }) {
  const steps = [
    {
      icon: UserRoundCheck,
      title: "Open and verify your account",
      body: "Create a free account, add your personal details, verify your identity with Aadhaar and PAN, and add the bank account your returns are paid into. Our team checks both.",
    },
    {
      icon: Layers,
      title: "Choose a plan and pay",
      body: "Pick a plan by its term, choose how many units to buy and see your monthly returns before you pay. Pay by bank or at our office; our finance team confirms the payment.",
    },
    {
      icon: FileSignature,
      title: "Accept your agreement",
      body: `Your investment agreement is ready once your payment is confirmed. Accept it and your term starts at the next full hour, with returns credited to your wallet ${credit}.`,
    },
    {
      icon: Landmark,
      title: "Withdraw to your bank",
      body: "Move your wallet balance to your verified bank account. Every credit and every withdrawal is listed in your account.",
    },
  ];
  return (
    <Section id="how-it-works" labelledBy="how-heading">
      <SectionHeader id="how-heading" pill="How It Works" lead="Four Steps From Sign‑Up" soft="To Your Bank Account" split
        action={<CtaLink href="/register">Create Your Account</CtaLink>}>
        No leasing experience needed. You can follow every step from your account.
      </SectionHeader>
      <div className="relative mt-12">
        {/* The line joining the steps, on wide screens. */}
        <span aria-hidden="true" className="absolute top-[3.25rem] right-[12%] left-[12%] hidden h-px bg-gradient-to-r from-transparent via-brass/60 to-transparent xl:block" />
        <ol className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {steps.map(({ icon: Icon, title, body }, index) => (
          <li key={title} className={`group relative space-y-4 rounded-3xl p-7 ${glass}`}>
            <div className="flex items-center justify-between">
              <span aria-hidden="true" className="flex size-12 items-center justify-center rounded-2xl bg-ink text-brass-on-ink shadow-lg shadow-ink/20">
                <Icon className="size-5" />
              </span>
              <span className="font-display text-sm font-semibold tracking-widest text-heading-soft">STEP 0{index + 1}</span>
            </div>
            <h3 className="text-lg font-semibold tracking-tight">{title}</h3>
            <p className="text-sm leading-relaxed text-muted">{body}</p>
          </li>
        ))}
        </ol>
      </div>
    </Section>
  );
}
