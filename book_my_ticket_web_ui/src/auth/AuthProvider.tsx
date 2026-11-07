import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { configureAuth } from '../api/http';
import {
  beginLogin, loadTokens, logoutUrl, profileFromToken, refresh, saveTokens, type TokenSet, type UserProfile,
} from './oidc';

export interface AuthContextValue {
  user: UserProfile | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (returnTo?: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Used after the OIDC callback stores fresh tokens. */
  setTokens: (tokens: TokenSet | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const REFRESH_SKEW_MS = 30_000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setTokensState] = useState<TokenSet | null>(() => {
    const t = loadTokens();
    return t && (t.expiresAt > Date.now() || t.refreshToken) ? t : null;
  });
  const tokensRef = useRef(tokens);
  tokensRef.current = tokens;
  const refreshing = useRef<Promise<TokenSet | null> | null>(null);

  const setTokens = useCallback((next: TokenSet | null) => {
    saveTokens(next);
    tokensRef.current = next;
    setTokensState(next);
  }, []);

  /** Returns a valid access token, refreshing (single-flight) when it is about to expire. */
  const getAccessToken = useCallback(async (): Promise<string | null> => {
    const current = tokensRef.current;
    if (!current) return null;
    if (current.expiresAt - REFRESH_SKEW_MS > Date.now()) return current.accessToken;
    if (!refreshing.current) {
      refreshing.current = refresh(current).finally(() => {
        refreshing.current = null;
      });
    }
    const next = await refreshing.current;
    setTokens(next);
    return next?.accessToken ?? null;
  }, [setTokens]);

  useEffect(() => {
    configureAuth(getAccessToken, () => setTokens(null));
  }, [getAccessToken, setTokens]);

  const value = useMemo<AuthContextValue>(() => {
    let user: UserProfile | null = null;
    try {
      user = tokens ? profileFromToken(tokens.accessToken) : null;
    } catch {
      user = null;
    }
    return {
      user,
      isAuthenticated: !!user,
      isAdmin: !!user?.roles.includes('ADMIN'),
      login: (returnTo) => beginLogin(returnTo ?? window.location.pathname + window.location.search),
      logout: async () => {
        const url = await logoutUrl(tokensRef.current);
        setTokens(null);
        window.location.assign(url);
      },
      setTokens,
    };
  }, [tokens, setTokens]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

export { AuthContext };
