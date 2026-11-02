import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DashboardSection } from "@/types/admin-portal";

export const metadata: Metadata = { title: "Dashboard" };

/** Sections follow the viewer's permissions: the API sends only what they may see. */
export default async function AdminDashboardPage() {
  const session = await requireStaff();
  const sections = await authFetch<DashboardSection[]>("/api/v1/admin/dashboard");

  return (
    <div className="space-y-8">
      <PageHeader title="Dashboard" description={`Signed in as ${session.roles.join(", ")}`} />
      {sections.length === 0 && <EmptyState title="Nothing to show yet" description="Your role has no dashboard data." />}
      {sections.map((section) => (
        <section key={section.key} aria-label={section.title} className="space-y-3">
          <h2 className="text-lg font-semibold tracking-tight">{section.title}</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {section.metrics.map((metric) => (
              <Link key={metric.label} href={metric.href}
                className="border border-border bg-surface p-4 transition-colors hover:border-gold">
                <p className="text-sm text-muted">{metric.label}</p>
                <p className="mt-1 font-display text-3xl font-semibold tabular-nums">{metric.value.toLocaleString("en-IN")}</p>
              </Link>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
