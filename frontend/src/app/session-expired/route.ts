import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { clearSessionCookies } from "@/lib/server/auth/cookies";

/**
 * Reached when the backend rejects a token the browser still holds (session revoked elsewhere).
 * Clears the cookies - which Server Components cannot do - so the proxy stops treating the user as
 * signed in, avoiding a /login <-> /dashboard redirect loop.
 */
export async function GET() {
  clearSessionCookies(await cookies());
  redirect("/login?expired=1");
}
