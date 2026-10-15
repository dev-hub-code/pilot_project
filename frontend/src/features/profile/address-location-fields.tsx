"use client";

import { useEffect, useState } from "react";
import { SelectField } from "@/components/ui/select-field";
import { TextField } from "@/components/ui/text-field";
import type { City, Country, State } from "@/types/location";

/** City choice meaning "not in the list": a text field takes the city instead. */
const OTHER = "__other__";

/** A list fetched for one key (a country code, a state id); null items: the request failed. */
type Fetched<T> = { key: string; items: T[] | null };

type Loaded<T> = { status: "idle" | "loading" | "failed" } | { status: "ready"; items: T[] };

/** Where a list stands for the current key: not asked for, on its way, failed, or here. */
function loaded<T>(key: string | null, fetched: Fetched<T> | null): Loaded<T> {
  if (key === null) return { status: "idle" };
  if (fetched?.key !== key) return { status: "loading" };
  return fetched.items === null ? { status: "failed" } : { status: "ready", items: fetched.items };
}

/** One level of the address data, from the app's own routes (which ask the backend). */
async function load<T>(url: string, signal: AbortSignal): Promise<T[]> {
  const response = await fetch(url, { signal });
  if (!response.ok) throw new Error(`${url}: ${response.status}`);
  return (await response.json()) as T[];
}

const sameName = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

/**
 * Country, state and city of an address, chosen one after the other: picking a country loads its states, picking a
 * state loads its cities. A country without states, a state without cities, or a city that is not listed is typed
 * instead. Posts {@code country} (ISO code), {@code stateRegion} and {@code city} (names).
 */
export function AddressLocationFields({ countries, defaults, errors }: {
  countries: Country[];
  defaults: { country: string; stateRegion: string; city: string };
  errors: { country?: string; stateRegion?: string; city?: string };
}) {
  const [country, setCountry] = useState(defaults.country);
  const [state, setState] = useState(defaults.stateRegion);
  const [cityChoice, setCityChoice] = useState("");
  const [cityText, setCityText] = useState(defaults.city);
  const [fetchedStates, setFetchedStates] = useState<Fetched<State> | null>(null);
  const [fetchedCities, setFetchedCities] = useState<Fetched<City> | null>(null);

  // Step 1 → 2: the chosen country's states.
  useEffect(() => {
    if (!country) return;
    const abort = new AbortController();
    load<State>(`/locations/countries/${encodeURIComponent(country)}/states`, abort.signal)
      .then((items) => {
        setFetchedStates({ key: country, items });
        // A saved state named differently ("maharashtra") becomes the listed one.
        setState((current) => items.find((s) => sameName(s.name, current))?.name ?? current);
      })
      .catch(() => {
        if (!abort.signal.aborted) setFetchedStates({ key: country, items: null });
      });
    return () => abort.abort();
  }, [country]);

  const states = loaded(country || null, fetchedStates);
  const stateList = states.status === "ready" ? states.items : [];
  const listedState = stateList.find((s) => sameName(s.name, state));

  // Step 2 → 3: the chosen state's cities.
  const stateId = listedState?.id;
  useEffect(() => {
    if (stateId === undefined) return;
    const abort = new AbortController();
    load<City>(`/locations/states/${stateId}/cities`, abort.signal)
      .then((items) => {
        setFetchedCities({ key: String(stateId), items });
        // The saved city, if listed, is picked; otherwise it stays typed in "Other".
        setCityChoice((current) => {
          if (current) return current;
          const typed = cityText.trim();
          if (!typed) return "";
          return items.find((c) => sameName(c.name, typed))?.name ?? OTHER;
        });
      })
      .catch(() => {
        if (!abort.signal.aborted) setFetchedCities({ key: String(stateId), items: null });
      });
    return () => abort.abort();
    // cityText is read once, when the cities arrive; typing must not reload them.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stateId]);

  const cities = loaded(stateId === undefined ? null : String(stateId), fetchedCities);
  const countryOptions = countries.map((c) => ({ value: c.code, label: c.name }));
  const stateOptions = stateList.map((s) => ({ value: s.name, label: s.name }));
  const cityList = cities.status === "ready" ? cities.items : [];
  const cityOptions = [...cityList.map((c) => ({ value: c.name, label: c.name })), { value: OTHER, label: "Other (not listed)" }];

  // Typed instead of picked: a country without states (or whose states could not be loaded), a state without cities.
  const typeState = states.status === "failed" || (states.status === "ready" && stateList.length === 0);
  const typeCity = typeState || cities.status === "failed" || (cities.status === "ready" && cityList.length === 0);

  return (
    <>
      <SelectField label="Country" name="country" options={countryOptions} placeholder="Select…" value={country}
        error={errors.country}
        onChange={(e) => {
          setCountry(e.target.value);
          setState("");
          setCityChoice("");
          setCityText("");
        }} />

      {typeState ? (
        <TextField label="State / region" name="stateRegion" value={state} error={errors.stateRegion}
          autoComplete="address-level1" onChange={(e) => setState(e.target.value)} />
      ) : (
        <SelectField label="State" name="stateRegion" options={stateOptions} value={listedState?.name ?? ""}
          placeholder={!country ? "Select a country first" : states.status === "loading" ? "Loading…" : "Select…"}
          disabled={states.status !== "ready"} error={errors.stateRegion} autoComplete="address-level1"
          onChange={(e) => {
            setState(e.target.value);
            setCityChoice("");
            setCityText("");
          }} />
      )}

      {typeCity ? (
        <TextField label="City" name="city" value={cityText} error={errors.city} autoComplete="address-level2"
          onChange={(e) => setCityText(e.target.value)} />
      ) : (
        <>
          <SelectField label="City" name={cityChoice === OTHER ? "citySelection" : "city"} options={cityOptions}
            value={cities.status === "ready" ? cityChoice : ""}
            placeholder={!listedState ? "Select a state first" : cities.status === "loading" ? "Loading…" : "Select…"}
            disabled={cities.status !== "ready"} error={cityChoice === OTHER ? undefined : errors.city}
            autoComplete="address-level2"
            onChange={(e) => {
              setCityChoice(e.target.value);
              if (e.target.value === OTHER) setCityText("");
            }} />
          {cityChoice === OTHER && (
            <TextField label="City name" name="city" value={cityText} error={errors.city} autoComplete="address-level2"
              onChange={(e) => setCityText(e.target.value)} />
          )}
        </>
      )}
    </>
  );
}
