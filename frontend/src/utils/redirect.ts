/** Only same-origin relative paths are accepted as post-login destinations (no open redirects). */
export function safeRedirectPath(candidate: unknown, fallback = "/dashboard"): string {
  if (
    typeof candidate !== "string" ||
    !candidate.startsWith("/") ||
    candidate.startsWith("//") ||
    candidate.includes("\\") ||
    // Browsers drop tabs and newlines from URLs, so "/\t/evil.com" would become "//evil.com".
    /[\u0000-\u001f\u007f]/.test(candidate)
  ) {
    return fallback;
  }
  return candidate;
}
