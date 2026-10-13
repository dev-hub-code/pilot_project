"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const AUTOPLAY_MS = 7000;
/** Above this many pages, a "3 / 15" counter replaces the dots. */
const MAX_DOTS = 8;

/**
 * A horizontal carousel of cards: one per view on phones, two on tablets, three on desktops. Swipe or scroll
 * natively (scroll snap), or use the arrows and dots; it pages a full view at a time. With `autoplay` it advances
 * every few seconds, pausing while the pointer or keyboard focus is on it, while the tab is hidden, and never for
 * reduced motion.
 */
export function Carousel({ label, itemsName, autoplay = false, itemClassName = "", children }: {
  /** Names the carousel for assistive technology, e.g. "Investor reviews". */
  label: string;
  /** What the cards are, in the plural, for button labels: "reviews", "plans". */
  itemsName: string;
  autoplay?: boolean;
  /** Layout of each card's box (padding, flex); the carousel sets its width and background. */
  itemClassName?: string;
  children: readonly React.ReactNode[];
}) {
  const count = children.length;
  const track = useRef<HTMLUListElement>(null);
  const [first, setFirst] = useState(0);
  const [perView, setPerView] = useState(1);
  const [paused, setPaused] = useState(false);
  const pages = Math.max(1, Math.ceil(count / perView));
  const page = Math.min(pages - 1, Math.ceil(first / perView));

  // Which card is first in view, and how many fit, follow the scroll position and the track's width.
  const measure = useCallback(() => {
    const el = track.current;
    const card = el?.firstElementChild as HTMLElement | null;
    if (!el || !card) return;
    const step = card.offsetWidth + 1;
    setPerView(Math.max(1, Math.round(el.clientWidth / step)));
    // At the far end the last page may be partly the previous one; count it as the last.
    const atEnd = el.scrollLeft >= el.scrollWidth - el.clientWidth - 2;
    setFirst(atEnd ? count - 1 : Math.round(el.scrollLeft / step));
  }, [count]);

  useEffect(() => {
    const el = track.current;
    if (!el) return;
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    el.addEventListener("scroll", measure, { passive: true });
    return () => {
      observer.disconnect();
      el.removeEventListener("scroll", measure);
    };
  }, [measure]);

  const goTo = useCallback((target: number) => {
    const el = track.current;
    const card = el?.firstElementChild as HTMLElement | null;
    if (!el || !card) return;
    const left = Math.min(target * perView * (card.offsetWidth + 1), el.scrollWidth - el.clientWidth);
    el.scrollTo({ left, behavior: "smooth" });
  }, [perView]);

  const last = pages - 1;
  const previous = () => goTo(page <= 0 ? last : page - 1);
  const next = useCallback(() => goTo(page >= last ? 0 : page + 1), [page, last, goTo]);

  useEffect(() => {
    if (!autoplay || paused || pages <= 1 || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") next();
    }, AUTOPLAY_MS);
    return () => window.clearInterval(timer);
  }, [autoplay, paused, pages, next]);

  return (
    <div role="region" aria-roledescription="carousel" aria-label={label}
      onPointerEnter={() => setPaused(true)} onPointerLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)} onBlurCapture={() => setPaused(false)}
      onKeyDown={(event) => {
        if (event.key === "ArrowLeft") previous();
        if (event.key === "ArrowRight") next();
      }}>
      {/* relative: absolutely positioned content in a card (e.g. an sr-only caption) stays clipped to the track. */}
      <ul ref={track}
        className="relative flex snap-x snap-mandatory gap-px overflow-x-auto overscroll-x-contain bg-border text-left [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {children.map((child, index) => (
          <li key={index} aria-roledescription="slide" aria-label={`${index + 1} of ${count}`}
            className={`w-full shrink-0 snap-start bg-surface sm:w-[calc((100%-1px)/2)] lg:w-[calc((100%-2px)/3)] ${itemClassName}`}>
            {child}
          </li>
        ))}
      </ul>

      {pages > 1 && (
        <div className="mt-6 flex items-center justify-between gap-4">
          {pages > MAX_DOTS ? (
            <p className="text-sm tabular-nums text-muted" aria-hidden="true">{page + 1} / {pages}</p>
          ) : (
            <div className="flex flex-wrap items-center gap-1" role="group" aria-label={`Choose ${itemsName}`}>
              {Array.from({ length: pages }, (_, index) => (
                <button key={index} type="button" onClick={() => goTo(index)}
                  aria-label={`Show page ${index + 1} of ${pages}`} aria-current={index === page ? "true" : undefined}
                  className="flex size-6 items-center justify-center rounded-full focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none">
                  <span className={`block h-1.5 rounded-full transition-all ${index === page ? "w-5 bg-gold" : "w-1.5 bg-border"}`} />
                </button>
              ))}
            </div>
          )}
          <div className="flex shrink-0 gap-2">
            <ArrowButton label={`Previous ${itemsName}`} direction="previous" onClick={previous} />
            <ArrowButton label={`Next ${itemsName}`} direction="next" onClick={next} />
          </div>
        </div>
      )}
      <p className="sr-only" aria-live="polite">
        Showing {itemsName} {page * perView + 1} to {Math.min((page + 1) * perView, count)} of {count}
      </p>
    </div>
  );
}

function ArrowButton({ label, direction, onClick }: { label: string; direction: "previous" | "next"; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-label={label}
      className="flex size-10 items-center justify-center rounded-full border border-border bg-surface text-foreground transition-colors hover:border-foreground focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none">
      <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8"
        strokeLinecap="round" strokeLinejoin="round">
        {direction === "previous" ? <path d="M15 6l-6 6 6 6" /> : <path d="M9 6l6 6-6 6" />}
      </svg>
    </button>
  );
}
