/**
 * Minimal, dependency-free OIDC Authorization Code + PKCE (S256) client for a public SPA client
 * ("bmtickets-web" in Keycloak locally, an app client in Amazon Cognito in AWS).
 *
 * Tokens live in sessionStorage (cleared when the tab closes) - never localStorage - to limit exposure.
 */
import { config } from '../config';

export interface OidcEndpoints {
  authorization_endpoint: string;
  token_endpoint: string;
  end_session_endpoint?: string;
}

export interface TokenSet {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  /** epoch millis */
  expiresAt: number;
}

export interface UserProfile {
  id: string;
  username: string;
  name: string;
  email: string | null;
  roles: string[];
}

const TOKENS_KEY = 'bmt.tokens';
const PENDING_KEY = 'bmt.oidc.pending';

let endpointsPromise: Promise<OidcEndpoints> | null = null;

export function keycloakFallbackEndpoints(issuer: string): OidcEndpoints {
  return {
    authorization_endpoint: `${issuer}/protocol/openid-connect/auth`,
    token_endpoint: `${issuer}/protocol/openid-connect/token`,
    end_session_endpoint: `${issuer}/protocol/openid-connect/logout`,
  };
}

/** OIDC discovery with a Keycloak-shaped fallback when the discovery document is not reachable (e.g. CORS). */
export function discover(issuer: string = config.authUrl): Promise<OidcEndpoints> {
  if (!endpointsPromise) {
    endpointsPromise = fetch(`${issuer}/.well-known/openid-configuration`)
      .then(async (r) => (r.ok ? ((await r.json()) as OidcEndpoints) : keycloakFallbackEndpoints(issuer)))
      .catch(() => keycloakFallbackEndpoints(issuer));
  }
  return endpointsPromise;
}

export function resetDiscoveryCache(): void {
  endpointsPromise = null;
}

// ---------------------------------------------------------------- PKCE helpers

export function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function randomString(byteLength = 32): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

export async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

export function redirectUri(): string {
  return `${window.location.origin}/auth/callback`;
}

// ---------------------------------------------------------------- token storage

export function loadTokens(): TokenSet | null {
  try {
    const raw = sessionStorage.getItem(TOKENS_KEY);
    return raw ? (JSON.parse(raw) as TokenSet) : null;
  } catch {
    return null;
  }
}

export function saveTokens(tokens: TokenSet | null): void {
  try {
    if (tokens) sessionStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
    else sessionStorage.removeItem(TOKENS_KEY);
  } catch {
    /* storage unavailable (private mode) - session stays in memory only */
  }
}

export function decodeJwt(token: string): Record<string, unknown> {
  const payload = token.split('.')[1];
  if (!payload) throw new Error('Malformed JWT');
  const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '='));
  return JSON.parse(decodeURIComponent(escape(json))) as Record<string, unknown>;
}

/** Roles from Keycloak (realm_access.roles) or Cognito (cognito:groups). */
export function profileFromToken(accessToken: string): UserProfile {
  const claims = decodeJwt(accessToken);
  const realmRoles = (claims.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
  const groups = (claims['cognito:groups'] as string[] | undefined) ?? [];
  const username = String(claims.preferred_username ?? claims.username ?? claims['cognito:username'] ?? claims.sub ?? 'guest');
  return {
    id: String(claims.sub ?? ''),
    username,
    name: String(claims.name ?? claims.given_name ?? username),
    email: typeof claims.email === 'string' ? claims.email : null,
    roles: [...new Set([...realmRoles, ...groups].map((r) => r.toUpperCase()))],
  };
}

function toTokenSet(json: Record<string, unknown>, previous?: TokenSet | null): TokenSet {
  return {
    accessToken: String(json.access_token),
    refreshToken: (json.refresh_token as string | undefined) ?? previous?.refreshToken ?? null,
    idToken: (json.id_token as string | undefined) ?? previous?.idToken ?? null,
    expiresAt: Date.now() + Number(json.expires_in ?? 300) * 1000,
  };
}

// ---------------------------------------------------------------- flows

export async function beginLogin(returnTo: string): Promise<void> {
  const endpoints = await discover();
  const verifier = randomString(48);
  const state = randomString(16);
  sessionStorage.setItem(PENDING_KEY, JSON.stringify({ verifier, state, returnTo }));
  const params = new URLSearchParams({
    client_id: config.clientId,
    response_type: 'code',
    scope: 'openid profile email',
    redirect_uri: redirectUri(),
    state,
    code_challenge: await pkceChallenge(verifier),
    code_challenge_method: 'S256',
  });
  window.location.assign(`${endpoints.authorization_endpoint}?${params}`);
}

/** Completes the redirect: validates state, exchanges the code, stores tokens. Returns the path to go back to. */
export async function completeLogin(search: string): Promise<{ tokens: TokenSet; returnTo: string }> {
  const params = new URLSearchParams(search);
  const error = params.get('error');
  if (error) throw new Error(params.get('error_description') ?? error);

  const pendingRaw = sessionStorage.getItem(PENDING_KEY);
  sessionStorage.removeItem(PENDING_KEY);
  if (!pendingRaw) throw new Error('No login in progress. Please sign in again.');
  const pending = JSON.parse(pendingRaw) as { verifier: string; state: string; returnTo: string };
  if (params.get('state') !== pending.state) throw new Error('Login state mismatch. Please sign in again.');
  const code = params.get('code');
  if (!code) throw new Error('Authorization code missing.');

  const endpoints = await discover();
  const response = await fetch(endpoints.token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      redirect_uri: redirectUri(),
      code_verifier: pending.verifier,
    }).toString(),
  });
  if (!response.ok) throw new Error('Could not complete sign-in.');
  const tokens = toTokenSet((await response.json()) as Record<string, unknown>);
  saveTokens(tokens);
  return { tokens, returnTo: safeReturnPath(pending.returnTo) };
}

export async function refresh(current: TokenSet): Promise<TokenSet | null> {
  if (!current.refreshToken) return null;
  const endpoints = await discover();
  const response = await fetch(endpoints.token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'refresh_token', client_id: config.clientId, refresh_token: current.refreshToken }).toString(),
  });
  if (!response.ok) return null;
  const next = toTokenSet((await response.json()) as Record<string, unknown>, current);
  saveTokens(next);
  return next;
}

export async function logoutUrl(tokens: TokenSet | null): Promise<string> {
  const endpoints = await discover();
  if (!endpoints.end_session_endpoint) return window.location.origin;
  const params = new URLSearchParams({ client_id: config.clientId, post_logout_redirect_uri: `${window.location.origin}/` });
  if (tokens?.idToken) params.set('id_token_hint', tokens.idToken);
  return `${endpoints.end_session_endpoint}?${params}`;
}

/** Only allow same-app relative paths after login (prevents open redirects). */
export function safeReturnPath(path: string | null | undefined): string {
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/auth/')) return '/';
  return path;
}
