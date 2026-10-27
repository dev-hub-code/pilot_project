import type { Metadata } from "next";
import Link from "next/link";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { PHOTO_CREDITS, PortPhoto } from "@/components/brand/port-photo";

export const metadata: Metadata = {
  title: "Photo Credits",
  description: "The photographers and licences of the photos on the SeaLease website.",
};

/** The credits the CC BY / CC BY-SA photos on the site require, linked from the landing page footer. */
export default function CreditsPage() {
  return (
    <div className="flex flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto w-full max-w-4xl bg-surface px-5 py-10 sm:px-10 lg:px-14 lg:py-14">
        <Logo />
        <div className="mt-12 space-y-4">
          <Eyebrow>Photo Credits</Eyebrow>
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">Photos On This Website</h1>
          <p className="max-w-2xl text-sm leading-relaxed text-muted">
            These photos of Indian ports are by the photographers below, from Wikimedia Commons, and are used under the
            Creative Commons licences shown. Our other photos are in the public domain.
          </p>
        </div>
        <ul className="mt-10 divide-y divide-border border-y border-border">
          {PHOTO_CREDITS.map((credit) => (
            <li key={credit.photo} className="flex flex-col gap-4 py-5 sm:flex-row sm:items-center">
              <PortPhoto name={credit.photo} alt="" sizes="160px" className="aspect-[4/3] w-40 shrink-0 object-cover" />
              <div className="space-y-1 text-sm">
                <p className="font-semibold">
                  <a href={credit.source} target="_blank" rel="noopener noreferrer" className="underline-offset-4 hover:underline">
                    {credit.title}
                  </a>
                </p>
                <p className="text-muted">
                  By {credit.author}, licensed under{" "}
                  <a href={credit.licenceUrl} target="_blank" rel="noopener noreferrer license" className="underline underline-offset-4">
                    {credit.licence}
                  </a>
                  {credit.changes && <>. {credit.changes} by SeaLease</>}
                </p>
              </div>
            </li>
          ))}
        </ul>
        <Link href="/" className="mt-10 inline-block text-sm font-semibold underline-offset-4 hover:underline">
          Back to the home page
        </Link>
      </div>
    </div>
  );
}
