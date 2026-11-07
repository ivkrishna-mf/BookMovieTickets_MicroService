import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Component, lazy, Suspense, type ErrorInfo, type ReactNode } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { ApiError } from './api/http';
import { AuthProvider } from './auth/AuthProvider';
import { Layout } from './components/Layout';
import { RequireAuth } from './components/RequireAuth';
import { ErrorState, Spinner } from './components/States';
import { CityProvider } from './hooks/city';
import { ToastProvider } from './hooks/toast';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { BrowsePage } from './pages/BrowsePage';
import { EventDetailsPage } from './pages/EventDetailsPage';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';

// Route-level code splitting: booking/payment/admin code loads only when needed.
const SeatSelectionPage = lazy(() => import('./pages/SeatSelectionPage').then((m) => ({ default: m.SeatSelectionPage })));
const CheckoutPage = lazy(() => import('./pages/CheckoutPage').then((m) => ({ default: m.CheckoutPage })));
const MyBookingsPage = lazy(() => import('./pages/MyBookingsPage').then((m) => ({ default: m.MyBookingsPage })));
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })));

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Retry only transient failures (network, gateway circuit-breaker fallback); never 4xx.
        retry: (failureCount, error) => error instanceof ApiError && error.isTransient && failureCount < 2,
        retryDelay: (attempt, error) =>
          error instanceof ApiError && error.retryAfterSeconds ? error.retryAfterSeconds * 1000 : Math.min(1000 * 2 ** attempt, 8000),
        refetchOnWindowFocus: false,
      },
      mutations: { retry: false },
    },
  });
}

class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI error', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return <ErrorState error={this.state.error} title="Something broke" onRetry={() => window.location.reload()} />;
    }
    return this.props.children;
  }
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="movies" element={<BrowsePage kind="movies" />} />
        <Route path="events" element={<BrowsePage kind="events" />} />
        <Route path="events/:id" element={<EventDetailsPage />} />
        <Route path="shows/:showId" element={<Suspense fallback={<Spinner />}><SeatSelectionPage /></Suspense>} />
        <Route
          path="bookings"
          element={<RequireAuth><Suspense fallback={<Spinner />}><MyBookingsPage /></Suspense></RequireAuth>}
        />
        <Route
          path="bookings/:id"
          element={<RequireAuth><Suspense fallback={<Spinner />}><CheckoutPage /></Suspense></RequireAuth>}
        />
        <Route
          path="admin"
          element={<RequireAuth role="ADMIN"><Suspense fallback={<Spinner />}><AdminPage /></Suspense></RequireAuth>}
        />
        <Route path="auth/callback" element={<AuthCallbackPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function AppProviders({ children, queryClient }: { children: ReactNode; queryClient: QueryClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CityProvider>
          <ToastProvider>
            <ErrorBoundary>{children}</ErrorBoundary>
          </ToastProvider>
        </CityProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}

const queryClient = createQueryClient();

export default function App() {
  return (
    <BrowserRouter>
      <AppProviders queryClient={queryClient}>
        <AppRoutes />
      </AppProviders>
    </BrowserRouter>
  );
}
