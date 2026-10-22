import type { ChangeEvent } from "react";

/**
 * For codes always written in capitals (PAN, IFSC): turns what is typed or pasted into upper case as it is
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

/** The characters a formatted number is made of; spaces, brackets and dashes are only layout. */
const significant = (value: string) => value.replace(/[^\d+]/g, "");

/**
 * Formats a number as it is typed or pasted ("1234 5678 9012", "98765 43210"), keeping the cursor after the same
 * digit. Deleting next to a space deletes the digit beside it (the space alone would only come back). For
 * uncontrolled inputs: `onChange={formatAsTyped(format)}`.
 */
export function formatAsTyped(format: (value: string) => string) {
  return (event: ChangeEvent<HTMLInputElement>): void => {
    const input = event.currentTarget;
    let value = input.value;
    let before = significant(value.slice(0, input.selectionStart ?? value.length)).length;
    const previous = input.dataset.formatted;
    if (previous !== undefined && value.length < previous.length && significant(value) === significant(previous)) {
      const digits = significant(value);
      const forward = (event.nativeEvent as InputEvent).inputType === "deleteContentForward";
      value = forward ? digits.slice(0, before) + digits.slice(before + 1) : digits.slice(0, before - 1) + digits.slice(before);
      if (!forward) before = Math.max(0, before - 1);
    }
    const formatted = format(value);
    input.value = formatted;
    input.dataset.formatted = formatted;
    let caret = 0;
    for (let seen = 0; caret < formatted.length && seen < before; caret++) {
      if (/[\d+]/.test(formatted.charAt(caret))) seen++;
    }
    if (document.activeElement === input) input.setSelectionRange(caret, caret);
  };
}

/** An Aadhaar number as printed on the card: "1234 5678 9012" (12 digits at most). */
export function formatAadhaarInput(value: string): string {
  return value.replace(/\D/g, "").slice(0, 12).replace(/(\d{4})(?=\d)/g, "$1 ");
}
