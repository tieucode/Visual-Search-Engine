import { Navigate, Outlet, Route, BrowserRouter, Routes } from 'react-router-dom';
import { ToastProvider } from './components/common/Toast';
import { AppHeader } from './components/layout/AppHeader';
import { AuthPage } from './pages/AuthPage';
import { UploadPage } from './pages/UploadPage';
import { getAccessToken } from './services/authToken';

function HomePage() {
  return (
    <main className="app-placeholder page-container">
      <div>
        <h1>Visual <span className="text-gradient">Search Engine</span></h1>
        <p>Đăng nhập thành công. Trang tìm kiếm sẽ được triển khai sau.</p>
      </div>
    </main>
  );
}

function ProtectedLayout() {
  return getAccessToken() ? (
    <>
      <AppHeader />
      <Outlet />
    </>
  ) : <Navigate to="/login" replace />;
}

function GuestAuth({ mode }: { mode: 'login' | 'register' }) {
  return getAccessToken() ? <Navigate to="/" replace /> : <AuthPage key={mode} mode={mode} />;
}

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<ProtectedLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/upload" element={<UploadPage />} />
            <Route
              path="/batches"
              element={
                <main className="app-placeholder page-container">
                  <div>
                    <h2>Tiến trình <span className="text-gradient">Indexing</span></h2>
                    <p>Trang quản lý toàn bộ batches sẽ được triển khai sau.</p>
                  </div>
                </main>
              }
            />
          </Route>
          <Route path="/login" element={<GuestAuth mode="login" />} />
          <Route path="/register" element={<GuestAuth mode="register" />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
}
