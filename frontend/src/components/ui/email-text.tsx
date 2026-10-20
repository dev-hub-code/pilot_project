import { Fragment } from "react";

/**
 * Text that may contain email addresses, with a line-break opportunity (`<wbr>`) before each "@". It reads and copies
 * the same, lets long addresses wrap on phones, and keeps proxies such as Cloudflare's email obfuscation from
 * rewriting the address in the HTML - which React would then report as a hydration mismatch.
 */
export function EmailText({ children }: { children: string | null | undefined }) {
  if (!children) return children ?? null;
  const parts = children.split("@");
  return parts.map((part, i) => (
    <Fragment key={i}>
      {i > 0 && <><wbr />@</>}
      {part}
    </Fragment>
  ));
}
