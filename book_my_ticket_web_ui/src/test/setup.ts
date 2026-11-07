import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { MotionGlobalConfig } from 'framer-motion';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';

// Absolute API base so fetch() works under Node (relative URLs need a browser).
window.__BMT_CONFIG__ = {
  apiBaseUrl: 'http://localhost:3000',
  authUrl: 'http://keycloak.test/realms/bmtickets',
  clientId: 'bmtickets-web',
  paymentMode: 'fake',
  defaultCity: 'Mumbai',
  cities: ['Mumbai', 'Delhi'],
};

// Animations are irrelevant to behaviour; skip them for speed and determinism.
MotionGlobalConfig.skipAnimations = true;

// jsdom gaps used by framer-motion / layout code.
if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    }),
  });
}
class ImmediateIntersectionObserver {
  private readonly cb: IntersectionObserverCallback;
  constructor(cb: IntersectionObserverCallback) {
    this.cb = cb;
  }
  observe(target: Element) {
    this.cb([{ isIntersecting: true, target, intersectionRatio: 1 } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
  }
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}
window.IntersectionObserver = ImmediateIntersectionObserver as unknown as typeof IntersectionObserver;
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;

const { server } = await import('./server');

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  // Node's fetch (undici) rejects jsdom's AbortSignal instances. Cancellation is irrelevant in tests,
  // so drop the signal. Installed after MSW so this wrapper is outermost.
  const mswFetch = globalThis.fetch;
  globalThis.fetch = (input: RequestInfo | URL, init?: RequestInit) => mswFetch(input, init && { ...init, signal: undefined });
});
afterEach(() => {
  server.resetHandlers();
  cleanup();
  sessionStorage.clear();
  localStorage.clear();
});
afterAll(() => server.close());
