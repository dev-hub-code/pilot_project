import { LogoMarkShapes } from "./logo";

/**
 * Hero illustration: a black corrugated shipping container at dusk in a container yard.
 * Pure SVG (no raster assets, no inline styles — compatible with the strict CSP), scaled to cover.
 */

const NEAR = { x: 600, top: 135, bottom: 890 };
const FAR = { x: 1310, top: 385, bottom: 830 };
const DOOR = { x: 175, top: 235, bottom: 880 };

function lerp(a: number, b: number, t: number) {
  return a + (b - a) * t;
}

/** Rib positions along the long side, denser towards the far end (perspective). */
function ribs(count: number) {
  return Array.from({ length: count }, (_, i) => {
    const t = (i + 1) / (count + 1);
    const u = 1 - Math.pow(1 - t, 1.45);
    const x = lerp(NEAR.x, FAR.x, u);
    return { x, top: lerp(NEAR.top, FAR.top, u) + 14, bottom: lerp(NEAR.bottom, FAR.bottom, u) - 12, i };
  });
}

function doorTop(x: number) {
  return lerp(DOOR.top, NEAR.top, (x - DOOR.x) / (NEAR.x - DOOR.x));
}

/**
 * @param focus which part stays visible when the frame is narrower than the scene:
 *              "center" (door and side) or "side" (the long, branded side — best behind text on the left)
 */
export function ContainerScene({ className = "", focus = "center" }: { className?: string; focus?: "center" | "side" }) {
  const bars = [230, 300, 470, 545];
  return (
    <svg
      viewBox="0 0 1440 900"
      preserveAspectRatio={focus === "side" ? "xMaxYMid slice" : "xMidYMid slice"}
      className={className}
      role="img"
      aria-label="A black SeaLease shipping container in a container yard at dusk"
    >
      <defs>
        <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#1c2330" />
          <stop offset="0.38" stopColor="#4d4a4c" />
          <stop offset="0.6" stopColor="#3f70a8" />
          <stop offset="0.72" stopColor="#5396e3" />
          <stop offset="0.78" stopColor="#7ab2f3" />
        </linearGradient>
        <radialGradient id="sun" cx="1180" cy="640" r="620" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#a8d1ff" stopOpacity="1" />
          <stop offset="0.3" stopColor="#5ea3f2" stopOpacity="0.6" />
          <stop offset="1" stopColor="#5aa0f0" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="ground" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#29313a" />
          <stop offset="1" stopColor="#090a0b" />
        </linearGradient>
        <linearGradient id="side" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#141416" />
          <stop offset="0.85" stopColor="#1c1b1c" />
          <stop offset="1" stopColor="#242e3a" />
        </linearGradient>
        <linearGradient id="door" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#0d0d0e" />
          <stop offset="1" stopColor="#19191b" />
        </linearGradient>
        <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#a4c8f2" />
          <stop offset="0.5" stopColor="#4c86c9" />
          <stop offset="1" stopColor="#2a578a" />
        </linearGradient>
        <filter id="soft" x="-20%" y="-200%" width="140%" height="500%">
          <feGaussianBlur stdDeviation="14" />
        </filter>
        <linearGradient id="legibility" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#000" stopOpacity="0.35" />
          <stop offset="0.25" stopColor="#000" stopOpacity="0" />
          <stop offset="0.6" stopColor="#000" stopOpacity="0.1" />
          <stop offset="1" stopColor="#000" stopOpacity="0.7" />
        </linearGradient>
      </defs>

      {/* Sky, sun glow and distant yard */}
      <rect width="1440" height="900" fill="url(#sky)" />
      <rect width="1440" height="900" fill="url(#sun)" />
      <g fill="#8abcf6" fillOpacity="0.22" filter="url(#soft)">
        <ellipse cx="1180" cy="250" rx="230" ry="22" />
        <ellipse cx="1290" cy="300" rx="170" ry="14" />
        <ellipse cx="980" cy="330" rx="200" ry="12" fillOpacity="0.12" />
        <ellipse cx="1350" cy="190" rx="120" ry="12" fillOpacity="0.1" />
      </g>
      <path d="M0 470 Q160 455 330 468 T700 470 T1080 462 T1440 470" fill="none" stroke="#a0c9f7" strokeOpacity="0.25" strokeWidth="2" />
      <g fill="#14171b">
        <rect x="1290" y="560" width="150" height="110" />
        <rect x="1330" y="500" width="110" height="62" />
        <rect x="0" y="380" width="120" height="300" />
        <rect x="0" y="330" width="160" height="56" fill="#1c2024" />
      </g>
      <g stroke="#22272c" strokeWidth="2">
        {Array.from({ length: 6 }, (_, i) => (
          <line key={i} x1={1300 + i * 22} y1="562" x2={1300 + i * 22} y2="668" />
        ))}
      </g>
      <rect x="0" y="420" width="190" height="4" fill="#6ba6e9" fillOpacity="0.6" />

      {/* Ground with a long reflection */}
      <rect y="660" width="1440" height="240" fill="url(#ground)" />
      <path d="M560 890 L1310 830 L1440 845 L1440 900 L560 900 Z" fill="#6aa9f0" fillOpacity="0.06" />

      {/* Long side of the container */}
      <polygon
        points={`${NEAR.x},${NEAR.top} ${FAR.x},${FAR.top} ${FAR.x},${FAR.bottom} ${NEAR.x},${NEAR.bottom}`}
        fill="url(#side)"
      />
      {ribs(34).map((rib) => (
        <g key={rib.i}>
          <line x1={rib.x} y1={rib.top} x2={rib.x} y2={rib.bottom} stroke="#2b2b2e" strokeWidth="5" />
          <line x1={rib.x + 4} y1={rib.top} x2={rib.x + 4} y2={rib.bottom} stroke="#060607" strokeWidth="3" />
        </g>
      ))}
      <line x1={NEAR.x} y1={NEAR.top} x2={FAR.x} y2={FAR.top} stroke="#6aa4e7" strokeOpacity="0.55" strokeWidth="5" />
      <line x1={NEAR.x} y1={NEAR.bottom} x2={FAR.x} y2={FAR.bottom} stroke="#050505" strokeWidth="10" />
      <line x1={FAR.x} y1={FAR.top} x2={FAR.x} y2={FAR.bottom} stroke="#5593d9" strokeOpacity="0.6" strokeWidth="5" />

      {/* Brand on the side, skewed to the side's perspective */}
      <g transform="matrix(1 0.13 0 1 720 330)">
        <g transform="translate(-5 10) scale(5)">
          <LogoMarkShapes ink="#c2cfdd" gold="url(#gold)" />
        </g>
        <text x="205" y="178" fill="#c2cfdd" className="font-wordmark" fontSize="54" fontWeight="600" letterSpacing="11">
          SEALEASE
        </text>
      </g>

      {/* Door end */}
      <polygon
        points={`${DOOR.x},${DOOR.top} ${NEAR.x},${NEAR.top} ${NEAR.x},${NEAR.bottom} ${DOOR.x},${DOOR.bottom}`}
        fill="url(#door)"
      />
      <polygon
        points={`${DOOR.x},${DOOR.top} ${NEAR.x},${NEAR.top} ${NEAR.x},${NEAR.top + 24} ${DOOR.x},${DOOR.top + 24}`}
        fill="#2a2a2d"
      />
      <line x1="385" y1={doorTop(385) + 30} x2="385" y2={DOOR.bottom - 18} stroke="#050505" strokeWidth="4" />
      {bars.map((x) => (
        <g key={x}>
          <line x1={x} y1={doorTop(x) + 34} x2={x} y2={DOOR.bottom - 30} stroke="#9a978f" strokeWidth="7" />
          <line x1={x - 3} y1={doorTop(x) + 34} x2={x - 3} y2={DOOR.bottom - 30} stroke="#d9d4c9" strokeOpacity="0.5" strokeWidth="1.5" />
          <rect x={x - 26} y="575" width="30" height="12" rx="2" fill="#82888f" />
        </g>
      ))}
      <g fill="#4c86c9" fillOpacity="0.6" fontFamily="'Arial Narrow', 'Roboto Condensed', monospace" fontSize="21">
        <text x="318" y="290" transform="rotate(-12 318 290)">SLSE 250001</text>
        <text x="318" y="318" transform="rotate(-12 318 318)">22G1</text>
        <text x="318" y="410" fontSize="13" transform="rotate(-12 318 410)">MAX. GROSS</text>
        <text x="318" y="428" fontSize="13" transform="rotate(-12 318 428)">30.480 KG</text>
        <text x="318" y="452" fontSize="13" transform="rotate(-12 318 452)">TARE 2.200 KG</text>
      </g>
      <g transform="translate(395 470)">
        <polygon points="0,52 20,6 28,6 8,52" fill="#4c86c9" fillOpacity="0.8" />
        <rect x="16" y="24" width="28" height="28" fill="none" stroke="#4c86c9" strokeOpacity="0.8" strokeWidth="3" />
      </g>
      <line x1={NEAR.x} y1={NEAR.top} x2={NEAR.x} y2={NEAR.bottom} stroke="#28303a" strokeWidth="6" />

      {/* Legibility overlay for text placed on top */}
      <rect width="1440" height="900" fill="url(#legibility)" />
    </svg>
  );
}
