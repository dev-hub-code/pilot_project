import "server-only";
import { z } from "zod";

/**
 * Server-side environment, validated once at first use. Nothing here is exposed to the browser:
 * the backend URL is reachable only from the Next.js server, which proxies API calls (BFF).
 */
const schema = z.object({
  BACKEND_URL: z.url().default("http://localhost:8080"),
  BACKEND_TIMEOUT_MS: z.coerce.number().int().positive().default(10_000),
  /** Must match the backend's app.auth.jwt.issuer / audience. */
  JWT_ISSUER: z.string().min(1).default("https://api.sealease.local"),
  JWT_AUDIENCE: z.string().min(1).default("sealease-api"),
  /** Secure cookies (HTTPS only). Defaults to on in production; only disable for local HTTP. */
  COOKIE_SECURE: z.stringbool().optional(),
});

export type ServerEnv = Omit<z.infer<typeof schema>, "COOKIE_SECURE"> & { COOKIE_SECURE: boolean };

let cached: ServerEnv | undefined;

export function serverEnv(): ServerEnv {
  if (!cached) {
    const parsed = schema.safeParse(process.env);
    if (!parsed.success) {
      throw new Error(`Invalid server environment: ${z.prettifyError(parsed.error)}`);
    }
    cached = {
      ...parsed.data,
      COOKIE_SECURE: parsed.data.COOKIE_SECURE ?? process.env.NODE_ENV === "production",
    };
  }
  return cached;
}
