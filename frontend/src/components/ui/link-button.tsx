import Link from "next/link";
import { buttonBase, buttonVariants } from "./button";

export function LinkButton({ href, variant = "primary", className = "", children }: {
  href: string;
  variant?: keyof typeof buttonVariants;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <Link href={href} className={`${buttonBase} ${buttonVariants[variant]} ${className}`}>
      {children}
    </Link>
  );
}
