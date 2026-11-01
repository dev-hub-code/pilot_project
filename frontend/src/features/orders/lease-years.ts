/** "1 year", "3 years". */
export function yearsLabel(years: number): string {
  return years === 1 ? "1 year" : `${years} years`;
}
