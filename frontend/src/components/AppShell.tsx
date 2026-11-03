import { useState } from "react";
import {
  ArrowUpRight,
  BookOpenCheck,
  CalendarCheck2,
  CalendarDays,
  History,
  Flag,
  LayoutDashboard,
  LogOut,
  Menu,
  NotebookTabs,
  ChartNoAxesCombined,
  GraduationCap,
  Download,
  Timer,
  X,
} from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const navigation = [
  { to: "/", label: "Overview", icon: LayoutDashboard, end: true },
  { to: "/goals", label: "Goals", icon: Flag },
  { to: "/tasks", label: "Daily tasks", icon: CalendarCheck2 },
  { to: "/focus", label: "Focus Arena", icon: Timer },
  { to: "/calendar", label: "Calendar", icon: CalendarDays },
  { to: "/history", label: "Study history", icon: History },
  { to: "/reports", label: "Reports", icon: ChartNoAxesCombined },
  { to: "/academic", label: "Academic progress", icon: GraduationCap },
  { to: "/exports", label: "Export data", icon: Download },
  { to: "/records", label: "Student records", icon: NotebookTabs },
];

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState("");
  const { logout } = useAuth();
  const navigate = useNavigate();

  const signOut = async () => {
    setSigningOut(true);
    setSignOutError("");
    try {
      await logout();
      navigate("/login", { replace: true });
    } catch (error) {
      setSignOutError(error instanceof Error ? error.message : "Unable to sign out securely.");
    } finally {
      setSigningOut(false);
    }
  };

  const closeMenu = () => setMenuOpen(false);

  return (
    <div className="app-shell min-h-screen bg-slate-50 text-slate-800">
      <div className="app-background" aria-hidden="true">
        <span className="ambient-orb ambient-orb-one" />
        <span className="ambient-orb ambient-orb-two" />
        <span className="ambient-orb ambient-orb-three" />
      </div>
      {menuOpen && <button className="mobile-backdrop" aria-label="Close navigation" onClick={closeMenu} />}
      <aside className={`sidebar ${menuOpen ? "sidebar-open" : ""}`}>
        <NavLink to="/" className="brand" onClick={closeMenu}>
          <span className="brand-mark"><BookOpenCheck size={20} /></span>
          <span>studyflow<span className="brand-period">.</span></span>
        </NavLink>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="side-nav" aria-label="Main navigation">
          {navigation.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={closeMenu}
              className={({ isActive }) => `nav-link ${isActive ? "nav-link-active" : ""}`}
            >
              <Icon size={18} strokeWidth={1.9} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="sidebar-note-icon"><ArrowUpRight size={16} /></span>
            <p>Your progress is built one focused session at a time.</p>
          </div>
          {signOutError && <p className="signout-error" role="alert">{signOutError}</p>}
          <button className="nav-link signout-button" onClick={() => void signOut()} disabled={signingOut}>
            <LogOut size={18} />
            <span>{signingOut ? "Logging out…" : "Logout"}</span>
          </button>
          <div className="sidebar-footnote">Personal learning, thoughtfully tracked.</div>
        </div>
      </aside>
      <div className="main-column">
        <header className="mobile-topbar">
          <button className="icon-button" aria-label={menuOpen ? "Close menu" : "Open menu"} onClick={() => setMenuOpen(!menuOpen)}>
            {menuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
          <NavLink to="/" className="brand brand-mobile" aria-label="Studyflow home">
            <span className="brand-mark"><BookOpenCheck size={18} /></span>
            <span>studyflow<span className="brand-period">.</span></span>
          </NavLink>
          <span className="mobile-avatar">S</span>
        </header>
        <main className="page-content"><Outlet /></main>
        <footer className="app-footer">
          <span>Studyflow</span>
          <span>Time tracked is progress noticed.</span>
        </footer>
      </div>
    </div>
  );
}
