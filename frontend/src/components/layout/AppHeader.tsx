import { LogOut, UploadCloud } from 'lucide-react';
import horizontalLogo from '../../assets/brand/visearch-logo-horizontal.png';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { clearAccessToken } from '../../services/authToken';
import { HeaderSearch } from '../search/HeaderSearch';
import '../../styles/layout.css';

export function AppHeader() {
  const navigate = useNavigate();

  return (
    <header className="app-header">
      <div className="app-header__inner page-container page-container--grid">
        <Link className="app-header__brand" to="/" aria-label="ViSearch">
          <img className="app-header__logo" src={horizontalLogo} alt="ViSearch" />
        </Link>

        <HeaderSearch />
        <nav className="app-header__nav" aria-label="Điều hướng chính">
          <NavLink to="/upload" className="app-header__nav-link" aria-label="Tải ảnh" title="Tải ảnh">
            <UploadCloud size={18} aria-hidden="true" />
            <span>Tải ảnh</span>
          </NavLink>
          <button
            type="button"
            className="icon-button app-header__logout"
            aria-label="Đăng xuất"
            title="Đăng xuất"
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
