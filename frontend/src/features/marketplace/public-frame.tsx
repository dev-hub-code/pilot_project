import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { LinkButton } from "@/components/ui/link-button";

/** The public marketplace's page: the site's framed sheet, with sign-in and sign-up at the top. */
export function PublicFrame({ next, children }: { next: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto w-full max-w-7xl bg-surface px-5 py-8 sm:px-10 lg:px-14 lg:py-10">
        <header className="flex flex-wrap items-center justify-between gap-4">
          <Logo />
          <nav className="flex items-center gap-3 text-sm">
            <Link href={`/login?next=${encodeURIComponent(next)}`} className="px-2 underline-offset-4 hover:underline">Sign in</Link>
            <LinkButton href="/register">Register</LinkButton>
          </nav>
        </header>
        <div className="mt-10">{children}</div>
      </div>
    </div>
  );
}
