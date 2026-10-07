import "server-only";
import { serverEnv } from "../env";

/** Tokens returned by the backend's /auth endpoints. */
export interface AuthTokens {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

interface CookieOptions {
  httpOnly: boolean;
  secure: boolean;
  sameSite: "lax" | "strict";
  path: string;
  expires?: Date;
}

/** Common surface of `cookies()` (Server Functions) and `NextResponse.cookies` (proxy). */
export interface CookieWriter {
  set(name: string, value: string, options?: CookieOptions): unknown;
  delete(name: string): unknown;
}

/**
 * Cookie names. With Secure cookies the `__Host-` prefix makes browsers enforce Secure, Path=/ and
 * no Domain attribute, so a sibling subdomain cannot plant or overwrite them.
 */
export function cookieNames() {
  const prefix = serverEnv().COOKIE_SECURE ? "__Host-" : "";
  return { access: `${prefix}sl_at`, refresh: `${prefix}sl_rt` } as const;
}

function baseOptions(): CookieOptions {
  return {
    httpOnly: true,
    secure: serverEnv().COOKIE_SECURE,
    // Lax: sent on top-level navigations (links from emails work) but not on cross-site
    // sub-requests or POSTs. Server Actions additionally verify the Origin header.
    sameSite: "lax",
    path: "/",
  };
}

export function writeSessionCookies(jar: CookieWriter, tokens: AuthTokens): void {
  const names = cookieNames();
  jar.set(names.access, tokens.accessToken, {
    ...baseOptions(),
    expires: new Date(tokens.accessTokenExpiresAt),
  });
  jar.set(names.refresh, tokens.refreshToken, {
    ...baseOptions(),
    expires: new Date(tokens.refreshTokenExpiresAt),
  });
}

export function clearSessionCookies(jar: CookieWriter): void {
  const names = cookieNames();
  jar.delete(names.access);
  jar.delete(names.refresh);
}
