#!/usr/bin/env node
/*
 * Signs in as a real employee of every staff role, calls every admin endpoint and opens every admin page, and compares
 * what happened with what the role's permissions say should happen. Creates accounts and data, so it refuses to run
 * unless E2E_DATABASE names the throwaway database the backend is using. See e2e/README.md.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { chromium } = require("playwright-core");

const API = process.env.E2E_API ?? "http://localhost:8080";
const WEB = process.env.E2E_WEB ?? "http://localhost:3001";
const ONLY = process.env.E2E_ONLY?.split(",");
const U = "00000000-0000-4000-8000-000000000001";
if (!/e2e/.test(process.env.E2E_DATABASE ?? "")) throw new Error("Set E2E_DATABASE to the throwaway database's name");
const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, "catalog.json")));
const PASSWORD = `E2e!${crypto.randomBytes(5).toString("hex")}9aZ`;
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function call(method, url, { token, json, form } = {}) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (method === "POST") headers["Idempotency-Key"] = crypto.randomUUID();
  if (json !== undefined) headers["Content-Type"] = "application/json";
  const r = await fetch(API + url, { method, headers, redirect: "manual", signal: AbortSignal.timeout(60000),
    body: form ?? (json === undefined ? undefined : JSON.stringify(json)) });
  const text = await r.text();
  let body = null;
  try { body = JSON.parse(text); } catch { /* not JSON */ }
  return { status: r.status, body };
}

/** Signs in, waiting out the limit on sign-ins from one address. */
async function login(email, password) {
  for (;;) {
    const r = await call("POST", "/api/v1/auth/login", { json: { email, password } });
    if (r.status === 429) { await wait(15000); continue; }
    if (r.status !== 200) throw new Error(`Sign-in failed for ${email}: ${r.status} ${JSON.stringify(r.body)}`);
    return r.body.accessToken;
  }
}

async function firstSignIn(email, temporary) {
  const token = await login(email, temporary);
  const changed = await call("POST", "/api/v1/auth/password", { token, json: { currentPassword: temporary, newPassword: PASSWORD } });
  if (changed.status >= 300) throw new Error(`Password change failed: ${changed.status} ${JSON.stringify(changed.body)}`);
  return login(email, PASSWORD);
}

const unique = () => crypto.randomBytes(5).toString("hex");
const stamp = (value, id) => JSON.parse(JSON.stringify(value).replaceAll("@DIGITS", String(parseInt(id, 16) % 100000000).padStart(8, "0")).replaceAll("@UNIQUEUPPER", id.toUpperCase()).replaceAll("@UNIQUE", id));

/** What a @PreAuthorize rule says for these permissions. */
function allows(rule, held) {
  const js = rule
    .replace(/hasAnyAuthority\(([^)]*)\)/g, (_, list) => `[${list}].some(has)`)
    .replace(/hasAuthority\(([^)]*)\)/g, (_, one) => `has(${one})`)
    .replace(/isAuthenticated\(\)/g, "true").replace(/\band\b/g, "&&").replace(/\bor\b/g, "||");
  return new Function("has", `return ${js}`)((p) => held.has(p));
}

const pageAllows = (page, held) => page.requires.length === 0
  || page.requires.some((need) => need.split("+").every((p) => held.has(p)));

async function probe(endpoint, token) {
  const url = endpoint.path.replace(/\{code\}/, "E2E").replace(/\{\w+\}/g, U) + (endpoint.query ? `?${endpoint.query}` : "");
  const options = { token };
  if (endpoint.multipart) { options.form = new FormData(); options.form.set("body", "e2e"); }
  else if (endpoint.body) options.json = stamp(endpoint.body, unique());
  return (await call(endpoint.method, url, options));
}

function judge(expected, r, anonymous) {
  if (r.status >= 500) return "SERVER_ERROR";
  if (anonymous) return r.status === 401 ? "PASS" : "UNEXPECTED_ACCESS";
  if (r.status === 401) return "NOT_SIGNED_IN";
  // The endpoint admits the role, but nobody may grant permissions they do not hold themselves.
  if (expected && r.status === 403 && /permissions you do not hold/.test(r.body?.message ?? "")) return "PASS_ESCALATION_GUARD";
  if (expected) return r.status === 403 ? "UNEXPECTED_REFUSAL" : "PASS";
  if (r.status === 403) return "PASS";
  return r.status === 400 ? "INCONCLUSIVE" : "UNEXPECTED_ACCESS";
}

async function visit(page, href) {
  const response = await page.goto(WEB + href, { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  const final = new URL(page.url()).pathname;
  const text = await page.locator("body").innerText().catch(() => "");
  if ((response?.status() ?? 200) >= 500 || /This page could not be loaded/.test(text)) return { outcome: "error", final };
  if (response?.status() === 404 || final !== href.split("?")[0] || /Not available/.test(text)) return { outcome: "blocked", final };
  return { outcome: "open", final };
}

async function main() {
  const signInAdmin = () => login(process.env.BOOTSTRAP_ADMIN_EMAIL, process.env.BOOTSTRAP_ADMIN_PASSWORD);
  let adminToken = await signInAdmin();
  const roles = (await call("GET", "/api/v1/admin/roles?size=200", { token: adminToken })).body.content;
  const executablePath = process.env.E2E_CHROME;
  const browser = await chromium.launch({ executablePath });
  const results = [];

  const anon = [];
  for (const e of catalog.endpoints) {
    const r = await probe(e, null);
    anon.push({ method: e.method, path: e.path, status: r.status, verdict: judge(false, r, true) });
  }
  results.push({ role: "ANONYMOUS", permissions: [], api: anon, pages: [], menu: [] });

  // Roles the tests themselves made are not part of the product.
  for (const role of roles.filter((r) => !r.name.startsWith("E2E_"))) {
    const investor = role.permissions.includes("INVESTOR_PORTAL");
    if (ONLY && !ONLY.includes(role.name)) continue;
    const id = unique();
    adminToken = await signInAdmin(); // Its access token outlives no more than a few roles.
    let token;
    if (role.name === "SUPER_ADMIN") token = adminToken;
    else if (investor) {
      const c = await call("POST", "/api/v1/admin/investors", { token: adminToken, json: stamp(catalog.endpoints.find((e) => e.path.endsWith("/admin/investors") && e.method === "POST").body, id) });
      token = await firstSignIn(c.body.email, c.body.temporaryPassword);
    } else {
      const c = await call("POST", "/api/v1/admin/staff", { token: adminToken, json: { email: `${id}@e2e.test`, firstName: "E2E", lastName: role.name, roles: [role.name] } });
      if (c.status !== 201) throw new Error(`Cannot create ${role.name}: ${c.status} ${JSON.stringify(c.body)}`);
      token = await firstSignIn(c.body.email, c.body.temporaryPassword);
    }
    const held = new Set(role.permissions);

    const api = [];
    for (const e of catalog.endpoints) {
      const expected = allows(e.rule, held);
      const r = await probe(e, token);
      api.push({ method: e.method, path: e.path, operation: e.operation, rule: e.rule, expected, status: r.status,
        code: r.body?.code ?? r.body?.errorCode ?? null, verdict: judge(expected, r, false) });
    }

    const context = await browser.newContext({ viewport: { width: 1400, height: 900 } });
    await context.addCookies([{ name: "sl_at", value: token, domain: "localhost", path: "/" }]);
    const page = await context.newPage();
    const home = await visit(page, "/admin");
    const menu = investor ? [] : await page.locator("aside nav a").evaluateAll((a) => a.map((x) => x.getAttribute("href")));
    const pages = [];
    for (const p of catalog.pages) {
      const expected = !investor && pageAllows(p, held);
      const seen = await visit(page, p.href);
      const inMenu = menu.includes(p.href);
      let verdict = "PASS";
      if (seen.outcome === "error") verdict = "PAGE_ERROR";
      else if (expected && seen.outcome !== "open") verdict = "BLOCKED_BUT_SHOULD_OPEN";
      else if (!expected && seen.outcome === "open") verdict = "OPEN_BUT_SHOULD_BLOCK";
      else if (p.menu && inMenu !== expected) verdict = "MENU_WRONG";
      pages.push({ href: p.href, label: p.label, menu: p.menu, inMenu, expected, outcome: seen.outcome, final: seen.final, verdict });
    }
    await context.close();
    results.push({ role: role.name, description: role.description, investor, system: role.system, departmentId: role.departmentId, permissions: role.permissions, api, pages, menu, home: home.outcome });
    const bad = api.filter((a) => a.verdict !== "PASS").length + pages.filter((a) => a.verdict !== "PASS").length;
    console.log(role.name, "api allowed", api.filter((a) => a.expected).length, "pages open", pages.filter((a) => a.outcome === "open").length, "problems", bad);
    fs.writeFileSync(path.join(__dirname, "results.json"), JSON.stringify(results, null, 1));
  }
  await browser.close();
}

main().catch((e) => { console.error("FAILED", e.message); process.exit(1); });
