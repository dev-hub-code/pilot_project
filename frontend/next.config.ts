import type { NextConfig } from "next";

/**
 * Baseline security headers for every route. A nonce-based Content-Security-Policy is added with
 * authentication (Phase 2), since it has to be generated per request in proxy.ts.
 */
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
];

/** Four KYC files of up to 5 MB each plus multipart overhead; matches the backend's limits. */
const UPLOAD_LIMIT = "22mb";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  experimental: {
    serverActions: { bodySizeLimit: UPLOAD_LIMIT },
    proxyClientMaxBodySize: UPLOAD_LIMIT,
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
