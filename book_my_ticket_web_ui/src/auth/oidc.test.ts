import { createHash } from 'node:crypto';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { loadConfig } from '../config';
import { fakeJwt } from '../test/fixtures';
import { server } from '../test/server';
import {
  completeLogin, keycloakFallbackEndpoints, loadTokens, pkceChallenge, profileFromToken, randomString, refresh, resetDiscoveryCache,
  safeReturnPath,
} from './oidc';

const ISSUER = 'http://keycloak.test/realms/bmtickets';

beforeEach(() => {
  resetDiscoveryCache();
  server.use(
    http.get(`${ISSUER}/.well-known/openid-configuration`, () => HttpResponse.json(keycloakFallbackEndpoints(ISSUER))),
  );
});

describe('PKCE', () => {
  it('computes the S256 challenge as BASE64URL(SHA256(verifier)) without padding', async () => {
    const verifier = randomString(48);
    const expected = createHash('sha256').update(verifier).digest('base64url'); // independent oracle (Node crypto)
    await expect(pkceChallenge(verifier)).resolves.toBe(expected);
    expect(verifier).toMatch(/^[A-Za-z0-9_-]{43,128}$/); // RFC 7636 verifier length & charset
  });
});

describe('profileFromToken', () => {
  it('reads Keycloak realm roles', () => {
    const p = profileFromToken(fakeJwt({ sub: 'u1', preferred_username: 'admin', realm_access: { roles: ['user', 'ADMIN'] } }));
    expect(p).toMatchObject({ id: 'u1', username: 'admin', roles: ['USER', 'ADMIN'] });
  });

  it('reads Cognito groups', () => {
    const p = profileFromToken(fakeJwt({ sub: 'u2', username: 'bob', 'cognito:groups': ['ADMIN'] }));
    expect(p.roles).toEqual(['ADMIN']);
    expect(p.username).toBe('bob');
  });
});

describe('safeReturnPath', () => {
  it('only allows same-app relative paths', () => {
    expect(safeReturnPath('/shows/1')).toBe('/shows/1');
    expect(safeReturnPath('https://evil.example')).toBe('/');
    expect(safeReturnPath('//evil.example')).toBe('/');
    expect(safeReturnPath('/auth/callback')).toBe('/');
    expect(safeReturnPath(null)).toBe('/');
  });
});

describe('completeLogin', () => {
  it('rejects a state mismatch (CSRF protection)', async () => {
    sessionStorage.setItem('bmt.oidc.pending', JSON.stringify({ verifier: 'v', state: 'expected', returnTo: '/' }));
    await expect(completeLogin('?code=abc&state=other')).rejects.toThrow(/state mismatch/i);
  });

  it('surfaces provider errors', async () => {
    await expect(completeLogin('?error=access_denied&error_description=User%20cancelled')).rejects.toThrow('User cancelled');
  });

  it('exchanges the code with the verifier and stores tokens', async () => {
    sessionStorage.setItem('bmt.oidc.pending', JSON.stringify({ verifier: 'the-verifier', state: 's1', returnTo: '/shows/9' }));
    let form: URLSearchParams | null = null;
    server.use(http.post(`${ISSUER}/protocol/openid-connect/token`, async ({ request }) => {
      form = new URLSearchParams(await request.text());
      return HttpResponse.json({ access_token: 'at', refresh_token: 'rt', id_token: 'it', expires_in: 300 });
    }));
    const { tokens, returnTo } = await completeLogin('?code=the-code&state=s1');
    expect(form!.get('grant_type')).toBe('authorization_code');
    expect(form!.get('code')).toBe('the-code');
    expect(form!.get('code_verifier')).toBe('the-verifier');
    expect(form!.get('client_id')).toBe('bmtickets-web');
    expect(tokens.accessToken).toBe('at');
    expect(returnTo).toBe('/shows/9');
    expect(loadTokens()?.refreshToken).toBe('rt');
  });
});

describe('refresh', () => {
  it('keeps the previous refresh token when the provider does not rotate it', async () => {
    server.use(http.post(`${ISSUER}/protocol/openid-connect/token`, () => HttpResponse.json({ access_token: 'new', expires_in: 60 })));
    const next = await refresh({ accessToken: 'old', refreshToken: 'rt', idToken: null, expiresAt: 0 });
    expect(next).toMatchObject({ accessToken: 'new', refreshToken: 'rt' });
  });

  it('returns null when the session is gone', async () => {
    server.use(http.post(`${ISSUER}/protocol/openid-connect/token`, () => new HttpResponse(null, { status: 400 })));
    await expect(refresh({ accessToken: 'old', refreshToken: 'rt', idToken: null, expiresAt: 0 })).resolves.toBeNull();
  });
});

describe('loadConfig', () => {
  it('normalises trailing slashes and unknown payment modes', () => {
    const c = loadConfig({ apiBaseUrl: 'https://api.example.com/', authUrl: 'https://id.example.com/realm/', paymentMode: 'paypal' as never });
    expect(c.apiBaseUrl).toBe('https://api.example.com');
    expect(c.authUrl).toBe('https://id.example.com/realm');
    expect(c.paymentMode).toBe('fake');
  });
});
