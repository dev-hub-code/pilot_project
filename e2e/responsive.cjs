#!/usr/bin/env node
/*
 * Opens every page at every screen size and records what does not fit: sideways page scrolling, content past the
 * screen edge, and controls too small to tap. Needs the same setup as run.cjs, plus E2E_INVESTOR_EMAIL/PASSWORD for
 * an investor with data. E2E_IDS is a JSON file of ids for detail pages. Writes e2e/responsive-results.json.
 */
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright-core");

const API = process.env.E2E_API ?? "http://localhost:8080";
const WEB = process.env.E2E_WEB ?? "http://localhost:3001";
const VIEWPORTS = [[320, 568, "small phone"], [360, 740, "phone"], [390, 844, "phone"], [430, 932, "large phone"],
  [568, 320, "small phone landscape"], [844, 390, "phone landscape"], [600, 960, "small tablet"], [768, 1024, "tablet"],
  [820, 1180, "tablet"], [1024, 768, "tablet landscape"], [1180, 820, "tablet landscape"], [1280, 800, "laptop"],
  [1440, 900, "laptop"], [1920, 1080, "desktop"], [2560, 1440, "large desktop"]];
const ids = process.env.E2E_IDS ? JSON.parse(fs.readFileSync(process.env.E2E_IDS)) : {};
const fill = (route) => route.replace(/\{(\w+)\}/g, (_, k) => ids[k] ?? "MISSING");

const PUBLIC = ["/", "/plans", "/terms", "/credits", "/login", "/register", "/forgot-password", "/reset-password", "/verify-email"];
const INVESTOR = ["/dashboard", "/earnings", "/invest", "/invest/{plan}", "/notifications", "/orders", "/orders/{order}",
  "/orders/{order}/invoice", "/portfolio", "/portfolio/{holding}/agreement", "/profile", "/profile/bank-accounts", "/profile/email",
  "/profile/verification", "/referrals", "/referrals/levels/1", "/settings", "/settings/notifications", "/settings/password",
  "/statements", "/support", "/support/new", "/support/{ticket}", "/withdrawals"];
const ADMIN = ["/admin", "/admin/agreement", "/admin/approvals", "/admin/audit", "/admin/demo-accounts", "/admin/departments",
  "/admin/departments/{department}", "/admin/downlines", "/admin/finance-tasks", "/admin/leads", "/admin/leads/{lead}", "/admin/leads/new",
  "/admin/ledger", "/admin/orders", "/admin/orders/{order}", "/admin/orders/{order}/invoice", "/admin/payments", "/admin/payouts",
  "/admin/permissions", "/admin/platform-config/company-accounts", "/admin/platform-config/referral-rates",
  "/admin/platform-config/returns", "/admin/platform-config/withdrawals", "/admin/products", "/admin/products/{plan}",
  "/admin/products/{plan}/edit", "/admin/products/new", "/admin/referrals", "/admin/reports", "/admin/roles", "/admin/roles/{role}",
  "/admin/roles/new", "/admin/security", "/admin/staff", "/admin/staff/{staff}", "/admin/staff/new", "/admin/support",
  "/admin/support/{ticket}", "/admin/terms", "/admin/users", "/admin/users/{investor}", "/admin/users/new-investor",
  "/admin/verification", "/admin/withdrawals", "/admin/withdrawals/batches"];

async function login(email, password) {
  for (;;) {
    const r = await fetch(`${API}/api/v1/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
    if (r.status === 429) { await new Promise((d) => setTimeout(d, 15000)); continue; }
    if (!r.ok) throw new Error(`Sign-in failed: ${r.status}`);
    return (await r.json()).accessToken;
  }
}

/** Runs in the page: everything that does not fit. */
function measure() {
  const vw = document.documentElement.clientWidth;
  const scrollsOrClips = (el) => {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const o = getComputedStyle(p).overflowX;
      if (o === "auto" || o === "scroll" || o === "hidden" || o === "clip") return true;
    }
    return false;
  };
  const name = (el) => `${el.tagName.toLowerCase()}${el.className && typeof el.className === "string" ? `.${el.className.trim().split(/\s+/).slice(0, 4).join(".")}` : ""} "${(el.innerText ?? "").trim().slice(0, 30)}"`;
  const visible = (el) => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== "hidden" && s.display !== "none"; };
  const past = [];
  for (const el of document.querySelectorAll("body *")) {
    if (!visible(el) || scrollsOrClips(el)) continue;
    const r = el.getBoundingClientRect();
    if (r.right < -1000) continue; // Deliberately off-screen, like the sign-up form's spam trap.
    if (r.right > vw + 1 || r.left < -1) past.push(name(el));
  }
  const tap = [];
  for (const el of document.querySelectorAll("a[href], button, input:not([type=hidden]), select, textarea, summary")) {
    if (!visible(el) || el.closest("p, label")) continue; // Links in a sentence; a label is itself the tap target.
    const r = el.getBoundingClientRect();
    if (r.height < 24 || r.width < 24) tap.push(name(el));
  }
  const scrollers = [...document.querySelectorAll("main *")].filter((el) => { const o = getComputedStyle(el).overflowX; return (o === "auto" || o === "scroll") && el.scrollWidth > el.clientWidth + 1; }).map((el) => `${name(el).slice(0, 60)} ${el.scrollWidth}/${el.clientWidth}`);
  return { overflow: document.documentElement.scrollWidth - vw, past: past.slice(0, 6), tap: tap.slice(0, 6), scrollers: scrollers.slice(0, 3) };
}

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROME });
  const results = [];
  for (const [width, height, device] of VIEWPORTS) {
    const sessions = [["public", null, PUBLIC],
      ["investor", await login(process.env.E2E_INVESTOR_EMAIL, process.env.E2E_INVESTOR_PASSWORD), INVESTOR],
      ["admin", await login(process.env.BOOTSTRAP_ADMIN_EMAIL, process.env.BOOTSTRAP_ADMIN_PASSWORD), ADMIN]];
    for (const [who, token, routes] of sessions) {
      const context = await browser.newContext({ viewport: { width, height }, isMobile: width < 1024, hasTouch: width < 1024 });
      if (token) await context.addCookies([{ name: "sl_at", value: token, domain: "localhost", path: "/" }]);
      const page = await context.newPage();
      for (const route of routes) {
        const url = fill(route);
        if (url.includes("MISSING")) continue;
        const response = await page.goto(WEB + url, { waitUntil: "domcontentloaded", timeout: 60000 });
        await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
        const failed = (response?.status() ?? 200) >= 500 || await page.getByText("This page could not be loaded").count() > 0;
        results.push({ who, route, width, height, device, failed, final: new URL(page.url()).pathname, ...(await page.evaluate(measure)) });
      }
      await context.close();
    }
    console.log(width, device, results.filter((r) => r.width === width && (r.overflow > 1 || r.past.length)).length, "pages do not fit");
    fs.writeFileSync(path.join(__dirname, "responsive-results.json"), JSON.stringify(results, null, 1));
  }
  await browser.close();
}
main().catch((e) => { console.error("FAILED", e.message); process.exit(1); });
