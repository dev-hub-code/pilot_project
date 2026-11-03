import { Fragment } from "react";

/**
 * Renders an article body written in light Markdown (## headings, - and 1. lists, blank-line paragraphs) as plain
 * React text nodes: nothing is ever injected as HTML, so an article cannot carry scripts or markup.
 */
export function ArticleBody({ body }: { body: string }) {
  const blocks = body.replace(/\r\n/g, "\n").split(/\n{2,}/).map((b) => b.trim()).filter(Boolean);
  return (
    <div className="space-y-4 text-[0.95rem] leading-7">
      {blocks.map((block, i) => {
        const lines = block.split("\n");
        if (/^#{1,3}\s/.test(block) && lines.length === 1) {
          const text = block.replace(/^#{1,3}\s+/, "");
          return block.startsWith("# ") || block.startsWith("## ")
            ? <h2 key={i} className="pt-2 text-lg font-semibold tracking-tight">{text}</h2>
            : <h3 key={i} className="pt-1 font-semibold">{text}</h3>;
        }
        if (lines.every((l) => /^[-*]\s+/.test(l))) {
          return <ul key={i} className="list-disc space-y-1 pl-6">{lines.map((l, j) => <li key={j}>{l.replace(/^[-*]\s+/, "")}</li>)}</ul>;
        }
        if (lines.every((l) => /^\d+[.)]\s+/.test(l))) {
          return <ol key={i} className="list-decimal space-y-1 pl-6">{lines.map((l, j) => <li key={j}>{l.replace(/^\d+[.)]\s+/, "")}</li>)}</ol>;
        }
        return <p key={i}>{lines.map((l, j) => <Fragment key={j}>{j > 0 && <br />}{l}</Fragment>)}</p>;
      })}
    </div>
  );
}
