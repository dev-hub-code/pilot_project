import type { ChangeEvent } from "react";

/**
 * For codes always written in capitals (PAN, GSTIN, IFSC): turns what is typed or pasted into upper case as it is
 * entered, keeping the cursor where it was. For uncontrolled inputs (`onChange={upperCase}`).
 */
export function upperCase(event: ChangeEvent<HTMLInputElement>): void {
  const input = event.currentTarget;
  const upper = input.value.toUpperCase();
  if (upper === input.value) return;
  const { selectionStart, selectionEnd } = input;
  input.value = upper;
  input.setSelectionRange(selectionStart, selectionEnd);
}
