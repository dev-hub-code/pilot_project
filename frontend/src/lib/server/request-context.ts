import "server-only";

/**
 * Headers that let the backend attribute a call to the end user rather than to this server:
 * client IP (for rate limiting and audit) and user agent. The backend trusts X-Forwarded-For only
 * from internal proxies and takes the right-most untrusted address, so production must run this
 * server behind an ingress that appends the real client IP.
 */
export function forwardedClientHeaders(incoming: Headers): Record<string, string> {
  const forwarded: Record<string, string> = {};
  const forwardedFor = incoming.get("x-forwarded-for");
  const userAgent = incoming.get("user-agent");
  if (forwardedFor) forwarded["X-Forwarded-For"] = forwardedFor;
  if (userAgent) forwarded["User-Agent"] = userAgent.slice(0, 512);
  return forwarded;
}
