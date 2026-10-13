/** "1 year", "3 years". */
export function yearsLabel(years: number): string {
  return years === 1 ? "1 year" : `${years} years`;
}

/** The leases an investor may choose under a plan: every whole year from its shortest to its longest. */
export function leaseOptions(minYears: number, maxYears: number): { value: string; label: string }[] {
  const options = [];
  for (let years = minYears; years <= maxYears; years++) options.push({ value: String(years), label: yearsLabel(years) });
  return options;
}
