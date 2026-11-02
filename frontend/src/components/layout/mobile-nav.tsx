"use client";

import { Menu, X } from "lucide-react";
import { useEffect, useRef } from "react";
import type { MenuSection } from "@/components/layout/account-menu";
import { NavLinks } from "@/components/layout/side-nav";

/** The site menu on screens too narrow for the side panel: a button in the header opens it as a drawer. */
export function MobileNav({ sections }: { sections: readonly MenuSection[] }) {
  const drawer = useRef<HTMLDialogElement>(null);
  const close = () => drawer.current?.close();

  // A drawer left open while the screen widens (a tablet turned sideways) would block the page behind it.
  useEffect(() => {
    const wide = window.matchMedia("(min-width: 64rem)");
    const onChange = () => { if (wide.matches) drawer.current?.close(); };
    wide.addEventListener("change", onChange);
    return () => wide.removeEventListener("change", onChange);
  }, []);

  return (
    <>
      <button type="button" aria-label="Open menu" onClick={() => drawer.current?.showModal()}
        className="-ml-2 flex size-11 shrink-0 items-center justify-center rounded-lg text-foreground hover:bg-background lg:hidden">
        <Menu aria-hidden="true" className="size-6" />
      </button>
      {/* A click on the backdrop lands on the dialog itself. */}
      <dialog ref={drawer} aria-label="Menu" onClick={(event) => { if (event.target === drawer.current) close(); }}
        className="m-0 h-dvh max-h-none w-72 max-w-[85vw] bg-surface text-foreground shadow-lg backdrop:bg-foreground/40 lg:hidden">
        <div className="flex h-full flex-col">
          <div className="flex justify-end border-b border-border p-2">
            <button type="button" aria-label="Close menu" onClick={close}
              className="flex size-11 items-center justify-center rounded-lg text-muted hover:bg-background hover:text-foreground">
              <X aria-hidden="true" className="size-6" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto"><NavLinks sections={sections} roomy onNavigate={close} /></div>
        </div>
      </dialog>
    </>
  );
}
