import { AnimatePresence, motion } from 'framer-motion';
import { Clapperboard, Home, LogIn, LogOut, MapPin, PartyPopper, ShieldCheck, Ticket, UserRound } from 'lucide-react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useCity } from '../hooks/city';
import { AnimatedBackground } from './AnimatedBackground';

interface Tab {
  to: string;
  label: string;
  icon: typeof Home;
  end?: boolean;
  adminOnly?: boolean;
}

export const NAV_TABS: Tab[] = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/movies', label: 'Movies', icon: Clapperboard },
  { to: '/events', label: 'Events', icon: PartyPopper },
  { to: '/bookings', label: 'My Bookings', icon: Ticket },
  { to: '/admin', label: 'Admin', icon: ShieldCheck, adminOnly: true },
];

function CitySelector() {
  const { city, cities, setCity } = useCity();
  return (
    <label className="city-select">
      <MapPin size={16} aria-hidden />
      <span className="sr-only">City</span>
      <select value={city} onChange={(e) => setCity(e.target.value)} aria-label="Select city">
        {cities.map((c) => (
          <option key={c} value={c}>{c}</option>
        ))}
      </select>
    </label>
  );
}

function AccountButton() {
  const { user, login, logout } = useAuth();
  if (!user) {
    return (
      <button className="btn btn--primary btn--sm" onClick={() => void login()}>
        <LogIn size={16} aria-hidden /> Sign in
      </button>
    );
  }
  return (
    <div className="account">
      <span className="account__avatar" aria-hidden>{user.name.slice(0, 1).toUpperCase()}</span>
      <span className="account__name"><UserRound size={14} aria-hidden /> {user.name}</span>
      <button className="icon-btn" onClick={() => void logout()} aria-label="Sign out" title="Sign out">
        <LogOut size={18} />
      </button>
    </div>
  );
}

export function Layout() {
  const location = useLocation();
  const { isAdmin } = useAuth();
  const tabs = NAV_TABS.filter((t) => !t.adminOnly || isAdmin);

  return (
    <div className="app-shell">
      <AnimatedBackground />
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="BookMyTickets home">
          <motion.span
            className="brand__logo"
            animate={{ rotateY: [0, 360] }}
            transition={{ duration: 6, repeat: Infinity, ease: 'linear', repeatDelay: 4 }}
            aria-hidden
          >
            🎟️
          </motion.span>
          <span className="brand__name">BookMy<span>Tickets</span></span>
        </NavLink>

        <nav className="tabs" aria-label="Primary">
          {tabs.map((tab) => (
            <NavLink key={tab.to} to={tab.to} end={tab.end} className={({ isActive }) => `tab ${isActive ? 'tab--active' : ''}`}>
              {({ isActive }) => (
                <>
                  {isActive && <motion.span layoutId="tab-pill" className="tab__pill" transition={{ type: 'spring', stiffness: 420, damping: 34 }} />}
                  <tab.icon size={16} aria-hidden />
                  <span>{tab.label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="topbar__actions">
          <CitySelector />
          <AccountButton />
        </div>
      </header>

      <main id="main" className="page-container">
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 24, rotateX: 6, filter: 'blur(6px)' }}
            animate={{ opacity: 1, y: 0, rotateX: 0, filter: 'blur(0px)' }}
            exit={{ opacity: 0, y: -16, rotateX: -4, filter: 'blur(4px)' }}
            transition={{ duration: 0.38, ease: [0.22, 1, 0.36, 1] }}
            style={{ transformPerspective: 1200 }}
          >
            <Outlet />
          </motion.div>
        </AnimatePresence>
      </main>

      <nav className="bottom-tabs" aria-label="Primary mobile">
        {tabs.map((tab) => (
          <NavLink key={tab.to} to={tab.to} end={tab.end} className={({ isActive }) => `bottom-tab ${isActive ? 'bottom-tab--active' : ''}`}>
            <tab.icon size={20} aria-hidden />
            <span>{tab.label.replace('My ', '')}</span>
          </NavLink>
        ))}
      </nav>

      <footer className="footer">
        <span>© {new Date().getFullYear()} BookMyTickets</span>
        <span>No double-booked seats. No double charges.</span>
      </footer>
    </div>
  );
}
