/**
 * Copies text to the clipboard; resolves to whether it worked. The Clipboard API exists only in secure contexts
 * (HTTPS or localhost), so on plain HTTP - e.g. the dev server opened by LAN IP - it falls back to the legacy
 * copy command on a temporary, off-screen textarea.
 */
export async function copyText(text: string): Promise<boolean> {
  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // Permission denied: try the fallback.
    }
  }
  const area = document.createElement("textarea");
  area.value = text;
  area.setAttribute("readonly", "");
  area.style.position = "fixed";
  area.style.opacity = "0";
  document.body.appendChild(area);
  const previous = document.activeElement as HTMLElement | null;
  area.select();
  try {
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    area.remove();
    previous?.focus();
  }
}
