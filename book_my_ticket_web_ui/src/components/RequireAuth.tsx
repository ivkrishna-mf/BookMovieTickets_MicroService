import { Lock, LogIn } from 'lucide-react';
import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { EmptyState } from './States';

export function RequireAuth({ children, role }: { children: ReactNode; role?: string }) {
  const { user, login } = useAuth();
  const location = useLocation();

  if (!user) {
    return (
      <EmptyState title="Sign in to continue" icon={<Lock size={40} aria-hidden />}>
        <p>Your bookings and tickets are tied to your BookMyTickets account.</p>
        <button className="btn btn--primary" onClick={() => void login(location.pathname + location.search)}>
          <LogIn size={16} aria-hidden /> Sign in
        </button>
      </EmptyState>
    );
  }
  if (role && !user.roles.includes(role)) {
    return (
      <EmptyState title="Restricted area" icon={<Lock size={40} aria-hidden />}>
        <p>This section requires the {role} role.</p>
      </EmptyState>
    );
  }
  return <>{children}</>;
}
