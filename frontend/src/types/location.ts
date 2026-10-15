/** One level of the address dropdowns, as the backend serves them (GET /api/v1/public/locations/...). */
export interface Country {
  /** ISO 3166-1 alpha-2, as addresses store it. */
  code: string;
  name: string;
  phoneCode: string | null;
}

export interface State {
  /** Asked for when loading the state's cities; addresses store the name. */
  id: number;
  name: string;
  code: string | null;
}

export interface City {
  id: number;
  name: string;
}
