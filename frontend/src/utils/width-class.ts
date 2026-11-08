/** A width class for a percentage, in steps of 10 (inline styles are blocked by the content security policy). */
export function widthClass(percent: number): string {
  const steps = ["w-0", "w-[10%]", "w-[20%]", "w-[30%]", "w-[40%]", "w-1/2", "w-[60%]", "w-[70%]", "w-[80%]", "w-[90%]", "w-full"];
  return steps[Math.max(0, Math.min(10, Math.round(percent / 10)))] ?? "w-full";
}
