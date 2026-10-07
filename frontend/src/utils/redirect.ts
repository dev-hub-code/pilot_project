/** Only same-origin relative paths are accepted as post-login destinations (no open redirects). */
export function safeRedirectPath(candidate: unknown, fallback = "/dashboard"): string {
  if (
    typeof candidate !== "string" ||
    !candidate.startsWith("/") ||
    candidate.startsWith("//") ||
    candidate.includes("\\")
  ) {
    return fallback;
  }
  return candidate;
}
