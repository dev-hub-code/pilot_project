import type { NextConfig } from "next";

/**
 * Baseline security headers for every route. A nonce-based Content-Security-Policy is added with
 * authentication (Phase 2), since it has to be generated per request in proxy.ts.
 */
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(self \"https://api.razorpay.com\")" },
  { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
];

/** Three files of up to 2 MB each (KYC documents or support attachments) plus multipart overhead; matches the backend. */
const UPLOAD_LIMIT = "8mb";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // A self-contained server (.next/standalone) for the production image (Dockerfile).
  output: "standalone",
  // `next dev` refuses its dev resources to hosts other than localhost; list LAN hosts/IPs here
  // (comma-separated) to open the dev server from another device, e.g. DEV_ALLOWED_ORIGINS=192.168.1.22
  allowedDevOrigins: process.env.DEV_ALLOWED_ORIGINS?.split(",").map((origin) => origin.trim()).filter(Boolean),
  reactStrictMode: true,
  experimental: {
    serverActions: { bodySizeLimit: UPLOAD_LIMIT },
    proxyClientMaxBodySize: UPLOAD_LIMIT,
  },
  // Pages that moved; old links keep working.
  async redirects() {
    return [
      { source: "/admin/kyc", destination: "/admin/verification", permanent: false },
      { source: "/admin/bank-accounts", destination: "/admin/verification", permanent: false },
      { source: "/admin/withdrawal-runs", destination: "/admin/platform-config/withdrawals", permanent: false },
      { source: "/admin/company-bank-accounts", destination: "/admin/platform-config/company-accounts", permanent: false },
      { source: "/admin/terms", destination: "/admin/platform-config/terms", permanent: false },
      { source: "/admin/agreement", destination: "/admin/platform-config/agreement", permanent: false },
      { source: "/admin/agreement/sample", destination: "/admin/platform-config/agreement/sample", permanent: false },
      { source: "/admin/agreement-reviews/:path*", destination: "/admin", permanent: false },
      { source: "/containers", destination: "/plans", permanent: true },
      { source: "/containers/:code", destination: "/plans/:code", permanent: true },
    ];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
