import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { config } from '../config';

const STORAGE_KEY = 'bmt.city';

interface CityContextValue {
  city: string;
  cities: string[];
  setCity: (city: string) => void;
}

const CityContext = createContext<CityContextValue | null>(null);

function readStoredCity(): string {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && config.cities.includes(stored)) return stored;
  } catch {
    /* storage unavailable */
  }
  return config.defaultCity;
}

export function CityProvider({ children, initialCity }: { children: ReactNode; initialCity?: string }) {
  const [city, setCityState] = useState(() => initialCity ?? readStoredCity());
  const setCity = useCallback((next: string) => {
    setCityState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* per-viewer convenience only */
    }
  }, []);
  const value = useMemo(() => ({ city, cities: config.cities, setCity }), [city, setCity]);
  return <CityContext.Provider value={value}>{children}</CityContext.Provider>;
}

export function useCity(): CityContextValue {
  const ctx = useContext(CityContext);
  if (!ctx) throw new Error('useCity must be used inside <CityProvider>');
  return ctx;
}
