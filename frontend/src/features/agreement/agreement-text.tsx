/**
 * Agreement wording laid out as the PDF lays it out: a blank line separates paragraphs and a line starting with "# "
 * is a heading (mirrors AgreementRenderer in the backend).
 */
export function AgreementText({ title, body }: { title: string; body: string }) {
  const blocks: { heading: boolean; text: string }[] = [];
  for (const block of body.replace(/\r\n/g, "\n").split(/\n\s*\n/)) {
    let text = block.trim();
    if (!text) continue;
    if (text.startsWith("# ")) {
      const end = text.indexOf("\n");
      blocks.push({ heading: true, text: (end < 0 ? text : text.slice(0, end)).slice(2).trim() });
      text = end < 0 ? "" : text.slice(end + 1).trim();
      if (!text) continue;
    }
    blocks.push({ heading: false, text });
  }
  return (
    <article className="space-y-3 border border-border bg-background p-6 text-sm leading-relaxed">
      <h3 className="text-xl font-semibold tracking-tight">{title}</h3>
      {blocks.map((b, i) => b.heading
        ? <h4 key={i} className="pt-2 font-semibold">{b.text}</h4>
        : <p key={i} className="whitespace-pre-line">{b.text}</p>)}
    </article>
  );
}
