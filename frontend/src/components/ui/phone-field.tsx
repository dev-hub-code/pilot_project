"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { formatPhoneInput, phoneCountryOf, phoneProblem } from "@/lib/phone";
import { formatAsTyped } from "@/utils/input";
import type { Country } from "@/types/location";

interface PhoneFieldProps {
  label: string;
  /** Countries with their dialling codes, as the backend stores them. */
  countries: Country[];
  /** The number without its country code, as last typed or saved. */
  defaultNumber?: string;
  defaultCountry: string;
  error?: string;
  /** Accept mobile numbers only (accounts); otherwise any valid number (leads). */
  mobile?: boolean;
  required?: boolean;
  id?: string;
  placeholder?: string;
}

/**
 * A phone number with its country: the country code (posted as {@code phoneCountry}) next to the number (posted as
 * {@code phone}). The number is checked when the field is left; the server checks again on submit.
 */
export function PhoneField({ label, countries, defaultNumber, defaultCountry, error, mobile = true, required, id = "phone",
  placeholder }: PhoneFieldProps) {
  const select = useRef<HTMLSelectElement>(null);
  const [problem, setProblem] = useState<string | null>(null);
  // The closed picker shows only the dialling code ("+91"); the list shows countries by name.
  const [country, setCountry] = useState(defaultCountry);
  const latestCountry = useRef(defaultCountry);
  useLayoutEffect(() => {
    latestCountry.current = defaultCountry;
  });

  // React resets a form after its action runs, which puts a <select> back on its first option: restore the country.
  useEffect(() => {
    const element = select.current;
    const form = element?.form;
    if (!element || !form) return;
    const restore = () => setTimeout(() => {
      element.value = latestCountry.current;
      setCountry(latestCountry.current);
    });
    form.addEventListener("reset", restore);
    return () => form.removeEventListener("reset", restore);
  }, []);

  /** @param adopt take the number's own country: when it was just typed, not when the country was just picked */
  function check(number: string, adopt: boolean) {
    const element = select.current;
    if (!element || number.trim() === "") {
      setProblem(null);
      return;
    }
    // "+44 7400 123456" typed with India chosen: switch the country to the number's.
    const belongsTo = adopt ? phoneCountryOf(number, element.value) : undefined;
    if (belongsTo && belongsTo !== element.value && countries.some((c) => c.code === belongsTo)) {
      element.value = belongsTo;
      setCountry(belongsTo);
    }
    relayout(element.value);
    setProblem(phoneProblem(number, element.value, mobile));
  }

  /** Lays the typed number out the way the chosen country writes it ("98765 43210"). */
  function relayout(chosen: string) {
    const number = select.current?.form?.elements.namedItem("phone");
    if (!(number instanceof HTMLInputElement) || number.value === "") return;
    number.value = formatPhoneInput(number.value, chosen);
    number.dataset.formatted = number.value;
  }

  const shown = problem ?? error;
  const dialCode = countries.find((c) => c.code === country)?.phoneCode;
  const errorId = `${id}-error`;
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-foreground">{label}</label>
      <div className="flex">
        <div className="relative flex h-11 w-24 shrink-0 items-center justify-between gap-1 border border-r-0 border-border bg-surface px-3 text-sm has-focus-visible:border-gold has-focus-visible:ring-1 has-focus-visible:ring-gold">
          <span aria-hidden="true">
            <span className="text-xs font-medium text-muted">{country}</span> +{dialCode}
          </span>
          <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4 text-muted" fill="currentColor">
            <path d="M5.5 7.5 10 12l4.5-4.5z" />
          </svg>
          <select ref={select} name="phoneCountry" aria-label="Country code" defaultValue={defaultCountry}
            onChange={(e) => {
              setCountry(e.currentTarget.value);
              relayout(e.currentTarget.value);
              const number = e.currentTarget.form?.elements.namedItem("phone");
              if (number instanceof HTMLInputElement && number.value.trim() !== "") check(number.value, false);
            }}
            className="absolute inset-0 cursor-pointer opacity-0">
            {countries.filter((c) => c.phoneCode).map((c) => (
              <option key={c.code} value={c.code}>{`${c.name} (+${c.phoneCode})`}</option>
            ))}
          </select>
        </div>
        <input id={id} name="phone" type="tel" inputMode="tel" autoComplete="tel-national" required={required}
          defaultValue={defaultNumber} placeholder={placeholder} maxLength={30}
          aria-invalid={shown ? true : undefined} aria-describedby={shown ? errorId : undefined}
          onBlur={(e) => check(e.currentTarget.value, true)}
          onChange={(e) => {
            formatAsTyped((value) => formatPhoneInput(value, select.current?.value ?? defaultCountry))(e);
            setProblem(null);
          }}
          className="h-11 w-full min-w-0 rounded-none border border-border bg-surface px-3 text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600" />
      </div>
      {shown && <p id={errorId} className="text-xs text-rose-600 dark:text-rose-400">{shown}</p>}
    </div>
  );
}
