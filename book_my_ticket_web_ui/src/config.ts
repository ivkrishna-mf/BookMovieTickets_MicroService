export type PaymentMode = 'fake' | 'stripe';

export interface AppConfig {
  /** Base URL of the API gateway. '' means same origin (dev proxy / ingress path routing). */
  apiBaseUrl: string;
  /** OIDC issuer URL (Keycloak realm locally, Cognito user pool in AWS). */
  authUrl: string;
  clientId: string;
  paymentMode: PaymentMode;
  stripePublishableKey: string;
  defaultCity: string;
  cities: string[];
}

declare global {
  interface Window {
    __BMT_CONFIG__?: Partial<AppConfig>;
  }
}

const DEFAULTS: AppConfig = {
  apiBaseUrl: '',
  authUrl: 'http://localhost:8180/realms/bmtickets',
  clientId: 'bmtickets-web',
  paymentMode: 'fake',
  stripePublishableKey: '',
  defaultCity: 'Mumbai',
  cities: ['Mumbai', 'Delhi', 'Bengaluru', 'Hyderabad', 'Chennai', 'Pune', 'Kolkata'],
};

export function loadConfig(
  raw: Partial<AppConfig> | undefined = typeof window !== 'undefined' ? window.__BMT_CONFIG__ : undefined,
): AppConfig {
  const merged = { ...DEFAULTS, ...(raw ?? {}) };
  return {
    ...merged,
    apiBaseUrl: merged.apiBaseUrl.replace(/\/+$/, ''),
    authUrl: merged.authUrl.replace(/\/+$/, ''),
    paymentMode: merged.paymentMode === 'stripe' ? 'stripe' : 'fake',
    cities: merged.cities.length ? merged.cities : DEFAULTS.cities,
  };
}

export const config: AppConfig = loadConfig();
