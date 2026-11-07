import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { completeLogin } from '../auth/oidc';
import { EmptyState, Spinner } from '../components/States';

export function AuthCallbackPage() {
  const { setTokens } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false); // StrictMode runs effects twice; the code can only be exchanged once.

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    completeLogin(window.location.search)
      .then(({ tokens, returnTo }) => {
        setTokens(tokens);
        navigate(returnTo, { replace: true });
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Sign-in failed'));
  }, [navigate, setTokens]);

  if (error) {
    return (
      <EmptyState title="Sign-in failed">
        <p>{error}</p>
        <Link to="/" className="btn btn--primary">Back to home</Link>
      </EmptyState>
    );
  }
  return <Spinner label="Signing you in" />;
}
