<<<<<<< Updated upstream
export default function App() {
  return (
    <main className="app-placeholder page-container">
      <div>
        <h1>Vi<span className="text-gradient">Search</span></h1>
        <p>Nền tảng frontend đã sẵn sàng.</p>
      </div>
    </main>
=======
import { Navigate, Route, BrowserRouter, Routes, useNavigate } from 'react-router-dom';
import { Button } from './components/common';
import { AuthPage } from './pages/AuthPage';
import { clearAccessToken, getAccessToken } from './services/authToken';

function HomePage() {
  const navigate = useNavigate();

  return (
    <main className="app-placeholder page-container">
      <div>
        <h1>Visual <span className="text-gradient">Search Engine</span></h1>
        <p>Đăng nhập thành công. Trang tìm kiếm sẽ được triển khai sau.</p>
        <Button
          variant="outline"
          className="app-placeholder__logout"
          onClick={() => {
            clearAccessToken();
            navigate('/login', { replace: true });
          }}
        >
          Đăng xuất
        </Button>
      </div>
    </main>
  );
}

function ProtectedHome() {
  return getAccessToken() ? <HomePage /> : <Navigate to="/login" replace />;
}

function GuestAuth({ mode }: { mode: 'login' | 'register' }) {
  return getAccessToken() ? <Navigate to="/" replace /> : <AuthPage key={mode} mode={mode} />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ProtectedHome />} />
        <Route path="/login" element={<GuestAuth mode="login" />} />
        <Route path="/register" element={<GuestAuth mode="register" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
>>>>>>> Stashed changes
  );
}
