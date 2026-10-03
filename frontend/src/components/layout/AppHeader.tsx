import { LogOut, ScanSearch, Search, UploadCloud } from 'lucide-react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { clearAccessToken } from '../../services/authToken';
import '../../styles/layout.css';

export function AppHeader() {
  const navigate = useNavigate();

  return (
    <header className="app-header glass">
      <div className="app-header__inner page-container page-container--grid">
        <Link className="app-header__brand" to="/" aria-label="ViSearch">
          <span className="app-header__mark"><ScanSearch size={18} strokeWidth={2.25} /></span>
          <span>Vi<span className="text-gradient">Search</span></span>
        </Link>

        <nav className="app-header__nav" aria-label="Main">
          <NavLink to="/" end className="icon-button" aria-label="Search" title="Search">
            <Search size={18} />
          </NavLink>
          <NavLink to="/upload" className="icon-button" aria-label="Upload" title="Upload">
            <UploadCloud size={18} />
          </NavLink>
          <button
            type="button"
            className="icon-button"
            aria-label="Sign out"
            title="Sign out"
            onClick={() => {
              clearAccessToken();
              navigate('/login', { replace: true });
            }}
          >
            <LogOut size={18} />
          </button>
        </nav>
      </div>
    </header>
  );
}
