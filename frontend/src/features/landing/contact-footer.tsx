import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { InterestForm } from "@/features/leads/interest-form";
import type { Country } from "@/types/location";
import { Heading, Pill, Section, glass, gutter } from "./landing-ui";

/** The enquiry form: it creates a lead for the sales team (POST /api/v1/public/leads). */
export function Contact({ countries }: { countries: Country[] }) {
  return (
    <Section id="contact" labelledBy="contact-heading">
      <div className={`grid gap-10 rounded-[2rem] p-6 sm:p-10 lg:grid-cols-[1fr_1.4fr] lg:p-14 ${glass}`}>
        <div className="space-y-5">
          <Pill>Contact</Pill>
          <Heading id="contact-heading" lead="Talk To" soft="An Investment Specialist" />
          <p className="max-w-md text-sm leading-relaxed text-muted">
            Tell us a little about what you are looking for and a specialist will get back to you, whether you are
            making your first investment or adding to one. There is no obligation to invest.
          </p>
        </div>
        <InterestForm countries={countries} />
      </div>
    </Section>
  );
}

const FOOTER_LINKS: { heading: string; links: { href: string; label: string }[] }[] = [
  {
    heading: "Invest",
    links: [
      { href: "/plans", label: "All plans" },
      { href: "/register", label: "Create an account" },
      { href: "/login", label: "Sign in" },
    ],
  },
  {
    heading: "Company",
    links: [
      { href: "#about", label: "About SeaLease" },
      { href: "#how-it-works", label: "How it works" },
      { href: "#network", label: "Logistics network" },
      { href: "#contact", label: "Contact" },
    ],
  },
  {
    heading: "Legal",
    links: [
      { href: "/terms", label: "Terms" },
      { href: "#disclosures", label: "Invest with confidence" },
      { href: "/credits", label: "Photo credits" },
    ],
  },
];

export function Footer() {
  return (
    <footer className={`bg-ink pt-14 pb-8 text-on-ink ${gutter}`}>
      <div className="grid gap-10 lg:grid-cols-[1.4fr_2fr]">
        <div className="max-w-sm space-y-4">
          <Logo tone="light" />
          <p className="text-sm leading-relaxed text-on-ink/70">
            Monthly returns from a container leasing business, with an agreement for every investment and every
            credit on record.
          </p>
        </div>
        <nav aria-label="Footer" className="grid grid-cols-2 gap-8 sm:grid-cols-3">
          {FOOTER_LINKS.map((group) => (
            <div key={group.heading}>
              <h2 className="text-xs font-semibold tracking-widest text-brass-on-ink uppercase">{group.heading}</h2>
              <ul className="mt-4 space-y-2.5 text-sm">
                {group.links.map((link) => (
                  <li key={link.href}>
                    {link.href.startsWith("#") ? (
                      <a href={link.href} className="text-on-ink/75 transition-colors hover:text-on-ink focus-visible:ring-2 focus-visible:ring-brass-on-ink focus-visible:outline-none">{link.label}</a>
                    ) : (
                      <Link href={link.href} className="text-on-ink/75 transition-colors hover:text-on-ink focus-visible:ring-2 focus-visible:ring-brass-on-ink focus-visible:outline-none">{link.label}</Link>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
      </div>
      <div className="mt-12 space-y-3 border-t border-on-ink/10 pt-6 text-xs leading-relaxed text-on-ink/60">
        <p>© {new Date().getFullYear()} Leasefleet Solutions Pvt. Ltd. All rights reserved.</p>
      </div>
    </footer>
  );
}
