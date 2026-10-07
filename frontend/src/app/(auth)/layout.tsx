import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";

export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="flex flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto grid w-full max-w-[1440px] bg-surface lg:grid-cols-[1.1fr_1fr]">
        <aside className="relative isolate hidden flex-col justify-between overflow-hidden bg-ink p-12 text-on-ink lg:flex">
          <ContainerScene focus="side" className="absolute inset-0 -z-10 size-full" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-ink via-ink/40 to-ink/20" />
          <Logo tone="light" />
          <div className="space-y-5">
            <Eyebrow tone="light">Container Investment Platform</Eyebrow>
            <p className="max-w-md text-4xl leading-tight font-bold tracking-tight">
              Real Assets. Real Rental Income. Every Cent On The Ledger.
            </p>
          </div>
        </aside>
        <main className="flex items-center justify-center px-5 py-12 sm:px-12">
          <div className="w-full max-w-md space-y-10">
            <div className="lg:hidden">
              <Logo />
            </div>
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
