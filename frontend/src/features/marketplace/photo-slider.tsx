"use client";

import { useCallback, useEffect, useRef, useState } from "react";

interface Photo {
  src: string;
  alt: string;
}

function Arrow({ direction }: { direction: "previous" | "next" }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-5">
      <path d={direction === "previous" ? "M15 5l-7 7 7 7" : "M9 5l7 7-7 7"} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * A slideshow of a plan's photos: swipe or scroll on touch screens (native scroll snapping), arrows and dots
 * with a mouse, arrow keys with the keyboard, and thumbnails to jump to a photo. No inline styles (the CSP
 * blocks them): slides are moved by scrolling the track.
 */
export function PhotoSlider({ photos }: { photos: Photo[] }) {
  const track = useRef<HTMLDivElement>(null);
  const [current, setCurrent] = useState(0);
  const count = photos.length;

  const go = useCallback((index: number) => {
    const el = track.current;
    if (!el) return;
    const target = (index + count) % count;
    el.scrollTo({ left: target * el.clientWidth, behavior: "smooth" });
  }, [count]);

  // Which slide is showing follows the scroll position, however it got there (swipe, arrows, thumbnails).
  useEffect(() => {
    const el = track.current;
    if (!el) return;
    const onScroll = () => setCurrent(Math.round(el.scrollLeft / Math.max(el.clientWidth, 1)));
    el.addEventListener("scroll", onScroll, { passive: true });
    return () => el.removeEventListener("scroll", onScroll);
  }, []);

  if (count === 0) return null;

  return (
    <div className="space-y-3">
      <div
        className="group relative aspect-[16/9] overflow-hidden bg-ink"
        role="region"
        aria-roledescription="carousel"
        aria-label="Container photos"
        tabIndex={count > 1 ? 0 : undefined}
        onKeyDown={(e) => {
          if (e.key === "ArrowLeft") { e.preventDefault(); go(current - 1); }
          if (e.key === "ArrowRight") { e.preventDefault(); go(current + 1); }
        }}
      >
        <div ref={track}
          className="flex size-full snap-x snap-mandatory overflow-x-auto overscroll-x-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {photos.map((photo, i) => (
            <div key={photo.src} className="size-full shrink-0 snap-center" role="group" aria-roledescription="slide"
              aria-label={`${i + 1} of ${count}`}>
              {/* eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP */}
              <img src={photo.src} alt={photo.alt} loading={i === 0 ? "eager" : "lazy"} draggable={false}
                className="size-full object-cover" />
            </div>
          ))}
        </div>

        {count > 1 && (
          <>
            <button type="button" onClick={() => go(current - 1)} aria-label="Previous photo"
              className="absolute top-1/2 left-3 grid size-10 -translate-y-1/2 place-items-center rounded-full bg-ink/60 text-on-ink transition-opacity hover:bg-ink/80 focus-visible:opacity-100 sm:opacity-0 sm:group-hover:opacity-100">
              <Arrow direction="previous" />
            </button>
            <button type="button" onClick={() => go(current + 1)} aria-label="Next photo"
              className="absolute top-1/2 right-3 grid size-10 -translate-y-1/2 place-items-center rounded-full bg-ink/60 text-on-ink transition-opacity hover:bg-ink/80 focus-visible:opacity-100 sm:opacity-0 sm:group-hover:opacity-100">
              <Arrow direction="next" />
            </button>
            <p className="absolute top-3 right-3 rounded-full bg-ink/60 px-2.5 py-1 text-xs text-on-ink tabular-nums" aria-live="polite">
              {current + 1} / {count}
            </p>
            <div className="absolute inset-x-0 bottom-3 flex justify-center gap-1.5">
              {photos.map((photo, i) => (
                <button key={photo.src} type="button" onClick={() => go(i)} aria-label={`Show photo ${i + 1}`}
                  aria-current={i === current ? "true" : undefined}
                  className={`h-1.5 rounded-full transition-all ${i === current ? "w-5 bg-on-ink" : "w-1.5 bg-on-ink/50 hover:bg-on-ink/80"}`} />
              ))}
            </div>
          </>
        )}
      </div>

      {count > 1 && (
        <ul className="flex gap-2 overflow-x-auto pb-1 [scrollbar-width:thin]" aria-label="Photo thumbnails">
          {photos.map((photo, i) => (
            <li key={photo.src} className="shrink-0">
              <button type="button" onClick={() => go(i)} aria-label={`Show photo ${i + 1}`}
                aria-current={i === current ? "true" : undefined}
                className={`block h-14 w-20 overflow-hidden bg-ink outline-offset-2 transition-opacity ${i === current ? "ring-2 ring-gold" : "opacity-60 hover:opacity-100"}`}>
                {/* eslint-disable-next-line @next/next/no-img-element -- see above */}
                <img src={photo.src} alt="" loading="lazy" className="size-full object-cover" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
